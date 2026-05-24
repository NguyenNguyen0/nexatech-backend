package com.nexatech.news.service;

import com.nexatech.news.dto.response.ImageAssetResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImageAssetService {
    ImageAssetResponse uploadNewsImage(MultipartFile file, String newsId, Long uploadedBy);
    
    ImageAssetResponse getNewsImage(String imageId);
    
    List<ImageAssetResponse> getNewsImagesByOwnerId(String newsId);
    
    void deleteAllImagesByOwnerId(String newsId);
    
    void deleteImageByPublicId(String publicId);
}
