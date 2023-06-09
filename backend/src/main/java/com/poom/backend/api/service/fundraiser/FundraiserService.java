package com.poom.backend.api.service.fundraiser;

import com.poom.backend.api.dto.fundraiser.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public interface FundraiserService {
    MyFundraiserListRes getMyFundraiserList(HttpServletRequest request, int size, int page, boolean isClosed);

    Long createFundraiser(HttpServletRequest request, List<MultipartFile> dogImages, MultipartFile nftImage, MultipartFile mainImage, OpenFundraiserCond openFundraiserCond);

    FundraiserListRes getFundraiserList(int size, int page, boolean isClosed);

    FundraiserDetailRes getFundraiserDetail(Long fundraiserId);

    void endFundraiser();

}
