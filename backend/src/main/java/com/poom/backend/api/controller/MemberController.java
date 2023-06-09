package com.poom.backend.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.filter.TokenFilter;
import com.poom.backend.api.dto.member.LoginRes;
import com.poom.backend.api.dto.member.MemberDto;
import com.poom.backend.api.service.member.MemberService;
import com.poom.backend.api.service.oauth.OauthServiceImpl;
import com.poom.backend.api.service.redis.RedisService;
import com.poom.backend.config.jwt.JwtFilter;
import com.poom.backend.config.jwt.TokenProvider;
import com.poom.backend.db.repository.MemberRepository;
import com.poom.backend.exception.BadRequestException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

@RestController
@Api(tags = "회원 관련 API")
@RequiredArgsConstructor
@Slf4j
public class MemberController {

    private final OauthServiceImpl oauthService;
    private final RedisService redisService;
    private final MemberRepository memberRepository;
    private final MemberService memberService;

    // 1. 로그인 (카카오 소셜 로그인만을 지원합니다.)
    @GetMapping("/member/login")
    @ApiOperation(value = "로그인(카카오)", notes = "<strong>code</strong>을 입력받아 로그아웃 처리를 합니다.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK(로그인 성공)"),
            @ApiResponse(code = 400, message = "BAD REQUEST(로그아웃 실패)"),
            @ApiResponse(code = 500, message = "서버 오류")
    })
    public ResponseEntity<?> kakaoLogin(HttpServletRequest request){
        String code = request.getHeader("Authorization");
        try {
            MemberDto res = oauthService.login("kakao", code);
            HttpHeaders headers = memberService.getHeader(res.getAccessToken(), res.getRefreshToken());
            return ResponseEntity.status(200).headers(headers).body(LoginRes.from(res.getMember()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // 2. 로그아웃
    @GetMapping("/member/logout")
    @ApiOperation(value = "로그아웃(카카오)", notes = "<strong>카카오 리다이렉트</strong>을 입력받아 로그아웃 처리를 합니다.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK(로그 아웃 성공)"),
            @ApiResponse(code = 500, message = "서버 오류")
    })
    public ResponseEntity<?> kakaoLogout(HttpServletRequest request){
        oauthService.logout(request);
        return ResponseEntity.status(200).build();
    }
    // 3. 회원 탈퇴
    @GetMapping("/member/withdrawal")
    @ApiOperation(value = "회원 탈퇴", notes = "<strong>카카오 리다이렉트</strong>을 입력받아 로그아웃 처리를 합니다.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK(회원 탈퇴 성공)"),
            @ApiResponse(code = 500, message = "서버 오류")
    })
    public ResponseEntity<?> withdrawalMember(@RequestParam String id){
        // redis에서 refresh 토큰을 삭제합니다.
//        redisService.deleteToken(id);
        // db에서 회원의 상태를 탈퇴 상태로 변경합니다.
        memberService.changeMemberStatusToWithdrawal(id);
        return ResponseEntity.status(200).build();
    }


    // 4. 내 정보 조회
    @GetMapping("/members")
    @ApiOperation(value = "회원 정보 조회", notes = "<strong>jwt 토큰 정보</strong>로 회원의 정보를 리턴합니다.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK(조회 성공)"),
            @ApiResponse(code = 400, message = "BAD REQUEST(요청 실패)"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED(권한 없음)"),
            @ApiResponse(code = 500, message = "서버에러")
    })
    public ResponseEntity<?> getMemberInfo(HttpServletRequest request){
        return ResponseEntity.status(200)
                .body(memberService.getMemberInfo(request));
    }

    // 5. 내 정보 수정
    @PutMapping("/members")
    @ApiOperation(value = "회원 정보 수정", notes = "<strong>멀티 파트 파일</strong>의 형태로 입력받아 회원의 닉네임, 프로필 사진을 변경한다.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK(수정 성공)"),
            @ApiResponse(code = 400, message = "BAD REQUEST(요청 실패)"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED(권한 없음)"),
            @ApiResponse(code = 500, message = "서버에러")
    })
    public ResponseEntity<?> updateMemberInfo(HttpServletRequest request,
                                              @RequestPart(name = "profileImage", required = false)MultipartFile profileImage,
                                              @RequestParam(required = false) String nickname){
        return ResponseEntity.status(200)
                .body(memberService.updateMemberInfo(request, profileImage, nickname));
    }

    // 5. 내 정보 수정
    @GetMapping("/member/refresh")
    @ApiOperation(value = "엑세스 토큰 재발급", notes = "<strong>멀티 파트 파일</strong>의 형태로 입력받아 회원의 닉네임, 프로필 사진을 변경한다.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK(발급 성공)"),
            @ApiResponse(code = 400, message = "BAD REQUEST(요청 실패, 서버에 저장된 refresh 토큰과 불일치)"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED(권한 없음)"),
            @ApiResponse(code = 500, message = "서버에러")
    })
    public ResponseEntity<?> refreshAccessToken(HttpServletRequest request){
        String memberId = memberService.getMemberIdFromHeader(request);
        log.info("멤버 ID : {}", memberId);
        log.info("REDIS에 있는 refresh Token : {} ", redisService.getRefreshToken(memberId));
        log.info("Request에서 보낸 refresh token : {}", memberService.getToken(request));

        if(!redisService.getRefreshToken(memberId).equals(memberService.getToken(request)))
            return ResponseEntity.status(400).build();
        MemberDto dto = oauthService.generateToken(memberRepository.findById(memberId)
                        .orElseThrow(()-> new BadRequestException("member가 없습니다.")));
        redisService.saveRefreshToken(memberId, dto.getRefreshToken());
        return ResponseEntity.status(200).headers(memberService.getHeader(dto.getAccessToken(), dto.getRefreshToken())).build();
    }


}
