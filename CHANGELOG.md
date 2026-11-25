# CHANGELOG

## 0.1.0 (25/11/2026)
Bản dựng lại đầu tiên (ADR-001), thay app cũ.

- Ghép file in lên cuộn khổ cố định, 3 cách xếp (vuông góc, nhồi hốc, theo hình thật). Đạt mốc nghiệm thu của app cũ: 478,2 cm, lấp đầy 92,45 %, 1 tấm.
- Xuất PDF vector, TIF CMYK có kênh mực trắng W1 và lớp trong suốt Photoshop, file CẮT cho Graphtec có dấu định vị.
- Mới so với app cũ: TIF tự dựng sẵn ở nền ngay khi ghép xong, có tiến độ từng tấm; bấm tấm đang chờ thì tấm đó được dựng trước. Ước lượng số mét tối thiểu ở bước 2, cảnh báo mẫu không xếp lồng được, xem trước vẽ đúng góc xoay, đơn đang soạn không mất khi tải lại trang.
- Máy chủ: chạy trong container giới hạn 1,5 GB RAM, có trần bộ nhớ chung cho mọi lần dựng TIF và file CẮT.
- Việc người quản lý làm tay: thêm bản ghi DNS, chép hồ sơ màu lên máy chủ, bấm deploy (xem `docs/phat-hanh/0.1.0.md`).
