package davmail.exchange.auth;

public class DeviceCode {
    final private String deviceCode;
    DeviceCode(String deviceCode) {
        this.deviceCode = deviceCode;
    }
    public String getDeviceCode() {
        return deviceCode;
    }
}
