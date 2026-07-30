package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.dto.OtpDTO;
import in.tracking.moneymanager.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/send")
    public ResponseEntity<OtpDTO.OtpResponse> sendOtp(@RequestBody OtpDTO.SendOtpRequest request) {
        otpService.sendOtp(request.phoneNumber());
        return ResponseEntity.ok(new OtpDTO.OtpResponse(true, "OTP sent successfully"));
    }

    @PostMapping("/resend")
    public ResponseEntity<OtpDTO.OtpResponse> resendOtp(@RequestBody OtpDTO.SendOtpRequest request) {
        otpService.sendOtp(request.phoneNumber());
        return ResponseEntity.ok(new OtpDTO.OtpResponse(true, "OTP resent successfully"));
    }

    @PostMapping("/verify")
    public ResponseEntity<OtpDTO.OtpResponse> verifyOtp(@RequestBody OtpDTO.VerifyOtpRequest request) {
        otpService.verifyOtp(request.phoneNumber(), request.code());
        return ResponseEntity.ok(new OtpDTO.OtpResponse(true, "Phone number verified successfully"));
    }
}
