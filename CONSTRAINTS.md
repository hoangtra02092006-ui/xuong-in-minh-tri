# CONSTRAINTS

Luật riêng của dự án. Mọi agent đọc trước khi code. Mỗi luật kiểm được, kèm lý do. Người quản lý duyệt khi thêm luật.
Nguồn gốc các luật: `docs/yeu-cau/dac-ta-goc.md` (gọi tắt "đặc tả"). Phá luật nào là in hỏng hàng thật.

## 12 bất biến (đặc tả mục 3), mỗi bất biến phải có test tự động
1. Không co giãn hình để vừa khổ: chỉ tịnh tiến và xoay. Lý do: in sai kích thước khách đặt.
2. Tổng bản đặt ra = tổng số lượng yêu cầu; lệch thì `NESTING_FAILED`, không trả bản thiếu.
3. Không chồng lấn, không ra ngoài khổ trừ lề, mọi cặp cách ≥ gap. Ở FREE / TRUE_SHAPE khung bao được chồng nhưng nét vẽ thật thì không.
4. Không tấm nào dài hơn `maxSheetLengthMm`.
5. Tất định: cùng đầu vào ra cùng toạ độ. Mọi `Random` có hạt cố định, mọi phép sắp có tie-break tới id. Lý do: thợ chạy lại phải ra đúng file đã duyệt.
6. Bản PDF giữ vector: PDF nguồn nhúng dạng form XObject, không raster hoá.
7. Đơn vị: giao diện cm, API mm (tối đa 2 số lẻ), engine số nguyên 1/100 mm, PDF point. Quy đổi chỉ ở `backend/.../shared/Units.java` và `frontend/src/shared/units.ts`.
8. Bản `allowRotate=false` giữ nguyên hướng; công tắc xoay toàn cục tắt thì ghi đè mọi dòng.
9. Kích thước báo lúc upload và hộp dùng để vẽ lúc xuất là CÙNG một content box (lưu lúc upload, không dùng `form.getBBox()`).
10. PDF, TIF, file cắt dựng từ cùng một bố trí; TIF và file cắt raster hoá từ chính bản PDF.
11. Chiều dài từng mẫu (`byType`) cộng lại đúng bằng tổng; không mẫu nào ra 0 khi có bản in. Chia theo diện tích riêng của bản. Lý do: chủ xưởng tính tiền giấy từng khách theo số này.
12. Không màn hình trắng: mọi trạng thái rỗng, đang tải, lỗi đều có nội dung và hành động tiếp theo.

## Số
- Engine dùng `long` 1/100 mm, không cộng dồn `double`. Số đo API là `double` mm làm tròn 2 số lẻ khi trả ra.
- Không có tiền trong app (chỉ số mét); không cần BigDecimal.
- `fillRate` dùng diện tích HỢP các khung bao (nén toạ độ), không cộng tổng; không bao giờ > 1.

## Bộ nhớ (đặc tả 10.6; máy chủ dùng chung với n8n, container 1,5 GB)
- Render TIF / file cắt theo dải ngang; chỉ mặt nạ 1 byte/pixel được giữ cả tấm.
- Dựng TIF ở nền lần lượt từng tấm, không song song; file dựng xong lưu ổ đĩa, không giữ trong RAM.
- Ước lượng bộ nhớ trước khi dựng; vượt `heap × APP_TIFF_MAX_HEAP_FRACTION` thì `TIFF_TOO_LARGE` kèm DPI gợi ý.
- Zip ghi thẳng ra luồng; tấm hỏng thì ghi `LOI-tamNN.txt` vào zip rồi làm tiếp, không để zip đứt.

## Code
- Backend chia theo nghiệp vụ: `engine` là Java thuần, không import Spring; phụ thuộc một chiều theo bản đồ module trong `docs/thiet-ke/`.
- Mọi lỗi API trả `{ "error": { "code", "message", "details" } }`; frontend phân nhánh theo `code`, không parse `message`.
- Câu chữ hiển thị là tiếng Việt có dấu, lấy đúng từ đặc tả mục 5 khi đặc tả có ghi.
- Thiết lập riêng của một module để trong lớp properties của module đó, không thêm vào `AppProperties` dùng chung.
- Ô nhập số dùng `type="text" inputmode="decimal"` và `parseDecimal` (bẫy đã gặp: `type="number"` + v-model làm ô nhập âm thầm không đổi).

## Dữ liệu và giấy phép
- Repo công khai: không commit hồ sơ màu Adobe (`*.icc`) và file in thật của xưởng (`samples/IN6*.tif`). Test cần chúng thì tự bỏ qua khi thiếu file (xem ADR-004).
- Không ghi mật khẩu, khoá SSH, địa chỉ nội bộ vào repo hay log.

## Môi trường
- Người quản lý dùng Windows: lệnh trong README chạy được ở PowerShell; Java dùng `Path`, không viết cứng `/`.
- Thời gian lưu UTC (ISO-8601), hiển thị theo giờ Việt Nam.
