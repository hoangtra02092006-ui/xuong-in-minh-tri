# ADR-003: Frontend Render, backend Docker sau nginx trên máy chủ Tino
Trạng thái: Đã duyệt (25/11)
Ngày: 2026-11-25

## Bối cảnh
Người quản lý có máy chủ Tino (Ubuntu 24.04, 6 GB RAM, đang chạy n8n và nhiều bot, nginx + certbot sẵn). Muốn backend chạy ở đây thay vì Render.

## Quyết định
- Frontend: Render Static Site, rewrite mọi đường về `index.html`.
- Backend: Docker, giới hạn container 1,5 GB, sau nginx có sẵn với server block riêng cho `in.thanhtungtextile.vn` (DNS ở Mắt Bão; đổi từ in.dreamate.me ngày 25/11 vì dreamate.me để DNS trên một tài khoản Cloudflare chưa truy cập được), chứng chỉ certbot. Không đụng site n8n.

## Phương án đã cân nhắc
- Thêm Caddy: tự cấp HTTPS gọn hơn nhưng tranh cổng 80/443 với nginx đang chạy.
- Backend trên Render: người quản lý không chọn.

## Hệ quả
Dùng chung RAM với n8n: tấm quá dài bị từ chối `TIFF_TOO_LARGE` sớm hơn bản cũ. Xem lại khi nâng gói Tino hoặc tách máy.
