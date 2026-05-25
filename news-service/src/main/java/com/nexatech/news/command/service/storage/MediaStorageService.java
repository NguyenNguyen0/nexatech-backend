package com.nexatech.news.command.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface MediaStorageService {

    StoredImage uploadImage(MultipartFile file, String folder) throws IOException;

    void deleteImage(String key) throws IOException;
}
