# TV Music & Mobile Server WiFi

Bộ ứng dụng nghe nhạc qua mạng Wi-Fi gồm **2 bản APK cài đặt song song**:
1. **TV Music** (`TV-Music-*.apk`): Ứng dụng nghe nhạc dành cho **Android TV & Android**, hỗ trợ duyệt thư mục USB/thẻ nhớ, hiệu ứng kính **Apple TV**, đổi ảnh nền qua mã QR và lấy nhạc trực tiếp từ điện thoại cùng mạng Wi-Fi.
2. **Mobile Server WiFi** (`Mobile-Server-WiFi-*.apk`): Ứng dụng chuyên dụng cho **điện thoại Android**, chọn **"Đặt máy này làm Server nhạc"** để Tivi quét và phát trực tiếp kho nhạc trên điện thoại mà không cần gửi file lên Tivi.

## Tính năng nổi bật
- **Đa định dạng âm thanh**: `FLAC`, `WAV`, `MP3`, `AAC / M4A`, `OGG`, `OPUS`.
- **Phát nhạc từ điện thoại qua Wi-Fi**: Bật **Mobile Server WiFi** trên điện thoại -> Trên TV chọn **Phát qua Wi-Fi** -> **Lấy nhạc trực tiếp từ ĐT**.
- **Ảnh bìa & Giao diện**: Tùy chọn **Đĩa xoay tròn** / **Vuông bo góc**, phong cách **Nền Trắng** hoặc **Apple TV (Hiệu ứng kính mờ tvOS)**.
- **Đổi ảnh nền qua mã QR**: Quét QR từ điện thoại (cùng Wi-Fi) hoặc chọn ảnh có sẵn, tùy chỉnh độ trong suốt `0% – 100%`.
- **Điều khiển linh hoạt**: Tối ưu cho cả **Remote TV (D-Pad)** và **Chuột / Air Mouse**.

## Tải file APK
Tải cả 2 file `.apk` (`TV-Music-...apk` và `Mobile-Server-WiFi-...apk`) tại mục **Releases** ở trang chủ repository (bên phải trang GitHub).

## Tự build APK
```bash
./gradlew assembleDebug
```
- APK bản Tivi: `app/build/outputs/apk/debug/app-debug.apk`
- APK bản điện thoại (`Mobile Server WiFi`): `mobile/build/outputs/apk/debug/mobile-debug.apk`
