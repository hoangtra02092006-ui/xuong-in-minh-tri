# Tóm tắt yêu cầu: dựng lại web Xưởng in Minh Trí

Đặc tả đầy đủ: [dac-ta-goc.md](dac-ta-goc.md) (người quản lý gửi 25/11). File này chỉ tóm phần cần duyệt và các giả định.

## Kết luận làm rõ
Đặc tả đã trả lời đủ 4 nhóm của skill lam-ro-yeu-cau, nên **không cần phiếu hỏi khách**:

| Nhóm | Đã rõ ở đâu (nguồn: đặc tả) |
|---|---|
| Lĩnh vực | Xưởng in DTF/decal, cuộn khổ cố định (thường 57 cm), tính tiền giấy theo mét dài (mục 1) |
| Thông tin | 4 màn hình (trang chủ, nạp file, tham số in, kết quả), cột bảng và câu chữ cụ thể (mục 5) |
| Nghiệp vụ | Nạp file → nhập khổ, lề, khoảng cách → ghép → tải TIF in → in xong mới cắt bằng file CẮT (mục 1, 5) |
| Đầu ra | PDF vector, TIF CMYK + W1 + lớp trong suốt, file CẮT cho Graphtec CE7000-60; thông số đối chiếu file mẫu thợ làm tay `samples/IN6 2209.tif` (mục 10) |

Thông số chuyên ngành (TIF, file cắt) đã có file mẫu thật làm chuẩn. Riêng **dấu định vị file cắt** chưa có file mẫu thật (đặc tả 10.5 tự ghi), nên giữ trong cấu hình và nghiệm thu bằng cắt thử.

## Phác thảo lời giải
- **Giữ:** 12 bất biến (mục 3), hợp đồng API `/api/v1` (mục 6), thuật toán và ngưỡng nghiệm thu ≥ 478,2 cm / 92,45 % (mục 8.6), các sửa lỗi của nhánh `fix/ram-va-chieu-dai-mau` (mục 12), yêu cầu không sập vì hết RAM (mục 10.6).
- **Làm chỉn chu hơn (đặc tả mục 16, 17):** câu chữ "tải TIF" thay "tải PDF", số kiểu Việt `0,3`, preview vẽ đúng góc xoay chéo, cảnh báo mẫu không đủ điều kiện xếp lồng, endpoint healthcheck, lưu đơn đang soạn vào trình duyệt.
- **Ngoài phạm vi:** tối ưu tốc độ dựng TIF / file cắt; bốn ô "Sắp có" ở trang chủ.

## Thay đổi người quản lý nêu thêm (25/11)
- Backend chạy trên **máy chủ riêng** của người quản lý (đang bật, vào bằng Termius), không trên Render. Frontend tĩnh vẫn trên Render. Thiếu phần mềm gì trên máy chủ thì agent tự cài.
- Agent làm việc **trên máy của người quản lý qua remote**, thao tác trong VS Code.
- Giai đoạn chuẩn bị chưa code thì được chia việc cho agent; khi code thì đi đúng quy trình plugin để người quản lý xem hiệu quả.
- Kiểm TIF và PDF bằng phần mềm Adobe đã có trên máy (Photoshop / Illustrator), theo checklist 10.4 và 10.5.

## Giả định của agent (người quản lý gật hoặc sửa)
1. Giữ stack cũ: Java 21 + Spring Boot 3 + PDFBox 3 + Maven; Vue 3 + TypeScript + Vite. Lý do: đã chứng minh đủ thư viện cho TIFF 5 kênh, ICC, form XObject, Separation/OCG; đổi stack là rủi ro lớn nhất của việc dựng lại.
2. Dựng ở **repo mới**, code cũ chỉ để đối chiếu và lấy `samples/`, hồ sơ ICC.
3. Giữ chế độ demo (mock) cho bản Render khi backend tắt, kèm test chạy mock với bộ nghiệm thu.
4. Backend chạy bằng Docker sau Caddy (tự cấp HTTPS) trên máy chủ riêng; một instance.
5. Chưa có database; kho job và kho file là interface như đặc tả.
