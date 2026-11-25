# ADR-001: Viết lại toàn bộ, app cũ làm chuẩn đối chiếu
Trạng thái: Đã duyệt (người quản lý chọn 25/11)
Ngày: 2026-11-25

## Bối cảnh
App cũ (repo `Push-clean-leather-file`, nhánh `fix/ram-va-chieu-dai-mau`) chạy được nhưng cấu trúc khó xem; người quản lý muốn dựng lại cùng chức năng, chỉn chu hơn.

## Quyết định
Viết mới toàn bộ theo đặc tả. App cũ chỉ dùng để đọc khi kẹt và để sinh bộ kết quả chuẩn (bố trí JSON, PDF, TIF, file cắt) trên `samples/`.

## Phương án đã cân nhắc
- Chuyển code cũ có chọn lọc: nhanh hơn 4–5 lần (4–6 ngày so với 3–4 tuần), giữ sẵn các sửa lỗi tinh vi; không chọn vì người quản lý muốn bản viết mới.

## Hệ quả
- Tốn khoảng 3–4 tuần agent. Rủi ro mất sửa lỗi cũ được chặn bằng: test viết trước cho mọi bẫy ở đặc tả mục 10, 12; so kết quả với bộ chuẩn từ app cũ; số nghiệm thu ≥ 478,2 cm / 92,45 %.
