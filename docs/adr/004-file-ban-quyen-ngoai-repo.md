# ADR-004: Hồ sơ màu Adobe và file in thật không nằm trong repo
Trạng thái: Đã duyệt (người quản lý chọn giữ repo công khai 25/11)
Ngày: 2026-11-25

## Bối cảnh
Repo công khai. TIF cần hồ sơ CMYK `USWebCoatedSWOP.icc` (Adobe phát hành) và được đối chiếu với `samples/IN6 2209.tif` (bản in thật của xưởng).

## Quyết định
Hai file này không commit (có trong `.gitignore`). Trên máy người quản lý và máy chủ, đặt ở thư mục riêng và trỏ bằng `APP_TIFF_CMYK_PROFILE`. Test cần chúng dùng `assumeTrue(file tồn tại)`: chạy đủ trên máy người quản lý, CI trên GitHub bỏ qua.

## Phương án đã cân nhắc
- Chuyển repo sang private và kèm file: CI chạy đủ; người quản lý không chọn.
- Dùng hồ sơ CGATS21 tự do: không vướng bản quyền nhưng màu lệch so với quy trình Photoshop của xưởng, file nặng thêm ~2,8 MB mỗi tấm.

## Hệ quả
CI không kiểm được màu CMYK thật; phải chạy test TIF trên máy người quản lý trước khi phát hành (ghi trong skill phát hành của dự án).
