package com.poom.backend.api.service.ipfs;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface IpfsService {

    String uploadJson(String json); // NFT의 메타데이터 IPFS에 저장 url로 return
    String downloadJson(String hash); // NFT의 메타데이터 IPFS에서 가져오기

    String uploadImage(MultipartFile file);
    MultipartFile downloadImage(String hash);

    String hashToUrl(String hash);
    String urlToHash(String hash);
    
}
