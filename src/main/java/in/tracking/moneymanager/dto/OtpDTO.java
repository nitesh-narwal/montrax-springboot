package in.tracking.moneymanager.dto;

public class OtpDTO {

    public record SendOtpRequest(String phoneNumber) {}

    public record VerifyOtpRequest(String phoneNumber, String code) {}

    public record OtpResponse(boolean success, String message) {}
}
