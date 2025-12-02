/*
 * Copyright (c) 2018-2999 广州市蓝海创新科技有限公司 All rights reserved.
 *
 * https://www.mall4j.com/
 *
 * 未经允许，不可做商业用途！
 *
 * 版权所有，侵权必究！
 */

package com.yami.shop.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;
import com.yami.shop.bean.enums.UploadType;
import com.yami.shop.bean.model.AttachFile;
import com.yami.shop.common.bean.Qiniu;
import com.yami.shop.common.util.ImgUploadUtil;
import com.yami.shop.common.util.Json;
import com.yami.shop.common.util.MinioHelper;
import com.yami.shop.dao.AttachFileMapper;
import com.yami.shop.service.AttachFileService;
import io.minio.MinioClient;
import jdk.jfr.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.Objects;

/**
 * @author lanhai
 */
@Service
public class AttachFileServiceImpl extends ServiceImpl<AttachFileMapper, AttachFile> implements AttachFileService {

    @Autowired
    private AttachFileMapper attachFileMapper;
    @Autowired
    private UploadManager uploadManager;
    @Autowired
    private BucketManager bucketManager;
    @Autowired
    private Qiniu qiniu;
    @Autowired
    private Auth auth;
    @Autowired
    private ImgUploadUtil imgUploadUtil;
    @Autowired
    private MinioHelper minioHelper;
    public final static String NORM_MONTH_PATTERN = "yyyy/MM/";

    private static final Logger logger = LoggerFactory.getLogger(AttachFileServiceImpl.class);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadFile(MultipartFile file) throws IOException {
        try {

            String extName = FileUtil.extName(file.getOriginalFilename());
            String fileName = DateUtil.format(new Date(), NORM_MONTH_PATTERN) + IdUtil.simpleUUID() + "." + extName;
            switch (imgUploadUtil.getUploadType()) {
                case 1:
                    AttachFile attachFile = new AttachFile();
                    attachFile.setFilePath(fileName);
                    attachFile.setFileSize(file.getBytes().length);
                    attachFile.setFileType(extName);
                    attachFile.setUploadTime(new Date());
                    // 本地文件上传
                    attachFileMapper.insert(attachFile);
                    return imgUploadUtil.upload(file, fileName);
                case 2:
                    // 七牛云文件上传
                    String upToken = auth.uploadToken(qiniu.getBucket(), fileName);
                    Response response = uploadManager.put(file.getBytes(), fileName, upToken);
                    Json.parseObject(response.bodyString(), DefaultPutRet.class);
                    return fileName;
                case 3:
                    logger.info("图片开始上传MINIO：{} ", fileName);
                    //minio
                    minioHelper.upload(file.getInputStream(), fileName, null);
                    return fileName;
                default:
                    break;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public void deleteFile(String fileName) {
        attachFileMapper.delete(new LambdaQueryWrapper<AttachFile>().eq(AttachFile::getFilePath, fileName));
        try {
            if (Objects.equals(imgUploadUtil.getUploadType(), UploadType.LOCAL.value())) {
                imgUploadUtil.delete(fileName);
            } else if (Objects.equals(imgUploadUtil.getUploadType(), UploadType.QINIU.value())) {
                bucketManager.delete(qiniu.getBucket(), fileName);
            } else if (Objects.equals(imgUploadUtil.getUploadType(), UploadType.MINIO.value())) {
                minioHelper.delete(fileName);
            }
        } catch (QiniuException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
