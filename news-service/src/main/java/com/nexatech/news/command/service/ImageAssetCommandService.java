package com.nexatech.news.command.service;

import com.nexatech.news.query.model.ImageAssetResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ImageAssetCommandService {
    ImageAssetResponse uploadNewsImage(MultipartFile file, String newsId, Long uploadedBy);

    void deleteAllImagesByOwnerId(String newsId);

    void deleteImageByPublicId(String publicId);
}
