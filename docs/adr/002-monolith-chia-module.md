# ADR-002: Backend monolith chia module theo nghiệp vụ
Trạng thái: Đã duyệt (thiết kế duyệt 25/11)
Ngày: 2026-11-25

## Bối cảnh
Một xưởng, vài thợ dùng cùng lúc, 4 màn hình, một người quản lý cùng agent bảo trì. Job đang để trong RAM.

## Quyết định
Một tiến trình Spring Boot, chia module `engine`, `nesting`, `file`, `raster`, `export`, `job`, `storage`, `api`, phụ thuộc một chiều. `engine` là Java thuần.

## Phương án đã cân nhắc
- Tách worker xuất file thành tiến trình riêng: chống việc dựng TIF làm chậm API, nhưng cần hàng đợi và database; quá sức ở cỡ hiện tại.

## Hệ quả
Deploy một image. Khi cần nhiều instance hoặc dựng TIF làm chậm thợ khác thì tách worker và chuyển job sang database.
