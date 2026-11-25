
# Đặc tả chi tiết để dựng lại "Xưởng in Minh Trí" (tên mã PrintNest)

> **Dành cho:** agent sẽ viết lại web này **cùng chức năng, giao diện có thể khác, làm chỉn chu hơn**.
> **Nguồn sự thật:** code trên nhánh `fix/ram-va-chieu-dai-mau` (PR đang mở, 6 commit trước `main`) — bản tốt nhất hiện có. Chỗ nào `docs/*.md` cũ nói khác code thì tài liệu này theo **code** (xem mục 16).
> **Ngoài phạm vi:** tối ưu tốc độ dựng TIF/file cắt — chủ dự án sẽ tự xử lý sau. Tài liệu chỉ yêu cầu bản mới **đúng** và **không sập vì hết RAM** (mục 10).
> **Triển khai đích:** frontend tĩnh trên **Render**, backend trên **máy chủ riêng** của chủ dự án (mục 14).

---

## Mục lục

1. Bài toán và người dùng
2. Thuật ngữ
3. Bất biến bắt buộc
4. Kiến trúc tổng thể
5. Giao diện: khung chung và từng màn hình
6. Hợp đồng API
7. Đọc file nguồn (upload)
8. Thuật toán xếp
9. Số liệu kết quả
10. Xuất PDF / TIF / file cắt
11. Vòng đời job, lưu trữ, dọn rác
12. Những gì nhánh hiện tại đã sửa (phải giữ)
13. Cấu hình (biến môi trường)
14. Triển khai: Render + máy chủ riêng
15. Kiểm thử và nghiệm thu
16. Chỗ tài liệu cũ lệch code
17. Gợi ý làm chỉn chu hơn

---

## 1. Bài toán và người dùng

**Xưởng in DTF/decal** in trên **cuộn khổ ngang cố định** (thường 57 cm), dài vô hạn, **tính tiền giấy theo mét dài**. Mỗi đơn có nhiều mẫu in (logo, nhãn…), mỗi mẫu một số lượng. Trước đây thợ xếp tay trong Illustrator — mất hàng giờ và vẫn phí giấy.

App làm ba việc:

1. **Xếp tự động (nesting):** đặt mọi bản in lên cuộn sao cho **tổng chiều dài nhỏ nhất**, tôn trọng khổ, lề, khoảng cách, cờ cấm xoay.
2. **Xuất file sản xuất** cho từng tấm:
   - **PDF** — bản in vector.
   - **TIF** — CMYK + **kênh mực trắng W1** + lớp trong suốt, đưa thẳng vào RIP máy in DTF. **Đây là định dạng xưởng dùng chính.**
   - **File CẮT** — PDF chỉ có đường cắt + 4 dấu định vị, gửi máy cắt **Graphtec CE7000-60** qua plugin **Cutting Master** (sau khi đã in xong).
3. **Chia tiền giấy:** báo mỗi mẫu "ăn" bao nhiêu mét, để chủ xưởng tính tiền từng khách.

**Người dùng:** thợ và chủ xưởng, vài người dùng đồng thời, mạng nội bộ, **không đăng nhập**. Họ nghĩ bằng **centimet** và **mét**. Toàn bộ giao diện bằng **tiếng Việt có dấu**.

---

## 2. Thuật ngữ

| Từ | Nghĩa |
|---|---|
| Khổ ngang | Chiều rộng cuộn (`sheetWidthMm`). Không bao giờ được vượt. |
| Lề biên | Khoảng chừa trống đều **bốn phía** tấm (`marginMm`). |
| Khoảng cách / gap | Khoảng hở **tối thiểu** giữa hai bản bất kỳ (`gapMm`). |
| Tấm / file | Một đoạn cuộn xuất thành một file. Có giới hạn chiều dài thì tự chia nhiều tấm. |
| Loại hình / mẫu | Một file nguồn đã tải lên. Có `categoryIndex` 0, 1, 2… theo thứ tự trong danh sách. |
| Bản | Một bản in của một mẫu (mẫu số lượng 50 → 50 bản). |
| Cắt trắng | Với PDF: bỏ khoảng trắng bao quanh, chỉ lấy vùng có nét vẽ. |
| Content box | Hộp bao vùng có nét vẽ của file nguồn (đơn vị point của PDF nguồn). |
| W1 | Kênh mực trắng lót (spot channel) trong TIF. |
| CutContour | Tên màu mực riêng mà Illustrator / Cutting Master nhận là đường cắt. |
| RIP | Phần mềm điều khiển máy in, nhận file TIF. |
| Dấu định vị | 4 ô vuông ở góc file cắt; camera máy cắt đọc để căn tấm phim. |

---

## 3. Bất biến bắt buộc

Phá một điều nào là in hỏng hàng thật. Mỗi điều phải có test tự động.

1. **Không bao giờ co giãn hình để vừa khổ.** Chỉ tịnh tiến và xoay. Kích thước in đúng bằng kích thước người dùng khai.
2. **Bảo toàn số lượng.** Tổng bản đặt ra = tổng số lượng yêu cầu. Lệch → báo lỗi `NESTING_FAILED`, không trả bản thiếu.
3. **Không chồng lấn, không ra ngoài vùng khổ trừ lề, mọi cặp bản cách nhau ≥ gap.** Ở hai chế độ xếp lồng, khung bao được phép chồng, nhưng **nét vẽ thật** thì không, và vẫn cách ≥ gap.
4. **Không tấm nào dài hơn `maxSheetLengthMm`.**
5. **Tất định.** Cùng đầu vào → cùng đầu ra, từng toạ độ. Thợ chạy lại phải ra đúng file đã duyệt. Mọi `Random` gieo hạt cố định; mọi phép sắp xếp có tie-break tới id.
6. **Bản PDF giữ nguyên vector.** File nguồn PDF nhúng dạng form XObject, không raster hoá. TIF là bản **thêm bên cạnh**.
7. **Đơn vị:** giao diện **cm**; API **mm** (số thực, tối đa 2 chữ số thập phân); engine **số nguyên 1/100 mm** (không cộng dồn số thực); PDF **point** (`mm × 72 / 25,4`). Mỗi tầng quy đổi ở **một chỗ duy nhất**.
8. **Bản có `allowRotate=false` giữ nguyên hướng.** Công tắc xoay toàn cục tắt thì ghi đè mọi dòng.
9. **Kích thước báo ra lúc upload và hộp dùng để vẽ lúc xuất phải là CÙNG một content box.** Lệch là in sai kích thước hoặc lệch vị trí.
10. **Ba định dạng xuất dựng từ cùng một bố trí** (TIF và file cắt raster hoá từ chính bản PDF) nên không thể lệch nhau.
11. **Chiều dài từng mẫu cộng lại đúng bằng tổng chiều dài**, và không mẫu nào được ra 0 khi có bản in.
12. **Không màn hình trắng.** Mọi trạng thái rỗng / đang tải / lỗi đều có nội dung hiển thị.

---

## 4. Kiến trúc tổng thể

```
Trình duyệt (SPA, deploy tĩnh trên Render)
   │  REST + JSON, đơn vị mm, CORS
   ▼
Backend monolith (máy chủ riêng)
   ├─ file/     nhận upload, đọc kích thước vật lý, ảnh xem trước
   ├─ nesting/  tạo job, chạy thuật toán ở luồng nền
   ├─ export/   dựng PDF / TIF / file cắt / zip
   ├─ job/      kho job (hiện trong RAM, sau thay DB được)
   └─ đĩa: thư mục storage/ chứa file gốc (tên = UUID)
```

- **Monolith, chia theo nghiệp vụ** (package-by-feature). Engine thuật toán là code thuần, không phụ thuộc web, test gọi thẳng được.
- **Job bất đồng bộ:** thuật toán tốn vài giây tới vài chục giây. `POST` trả `202` + `jobId` ngay; frontend hỏi trạng thái mỗi 700 ms.
- **Chưa có database:** file trên đĩa, job và metadata trong RAM. Khởi động lại là mất (thông báo lỗi phải nói rõ điều này). Kho job và kho file nên là **interface** để sau thay bằng DB.
- **Stack hiện tại** (không bắt buộc giữ): Java 17+ / Spring Boot 3.3 / Apache PDFBox 3 / Maven; Vue 3 + TypeScript + Vite + Pinia + Vue Router + Axios, tự viết ~12 component UI trên CSS token. Đổi ngôn ngữ backend thì phải có thư viện làm được: đọc content stream PDF, nhúng trang PDF làm form XObject, render PDF ra bitmap có alpha, chuyển màu ICC, ghi TIFF 5 kênh với tag tuỳ biến, ghi PDF có Separation colorspace và Optional Content Group.

---

## 5. Giao diện

Giao diện được thiết kế lại tự do. Mục này mô tả **hành vi, nội dung và câu chữ** phải giữ.

### 5.1 Khung chung

- **Header dính trên cùng:** logo + "**Xưởng in Minh Trí**" + dòng phụ "Ghép file in tiết kiệm khổ" (bấm về trang chủ). Bên phải: badge "**Chế độ demo**" nếu đang chạy mock; nút "**Trợ giúp**" hiện toast: "Quy trình: nạp file in → nhập khổ ngang, lề, khoảng cách → bấm Ghép file → tải PDF về gửi máy in." (nên sửa thành "tải TIF").
- **Tiêu đề tab:** `"<tên màn> - Xưởng in Minh Trí"`. Tên màn: Công cụ sản xuất · Nạp file · Tham số in · Kết quả · Không tìm thấy trang.
- **Stepper 3 bước** (Nạp file → Tham số in → Kết quả) trên ba màn wizard. Bước đã qua bấm quay lại được; bước chưa tới **khoá thật** (disabled), không chỉ làm mờ.
- **Mỗi màn chỉ một nút chính (primary).**
- **Toast** góc trên phải, tự ẩn sau **4 giây**, ba loại success / error / info. Dùng cho lỗi thao tác lẻ.
- **Khối trạng thái rỗng / lỗi** (empty state có biến thể lỗi) cho lỗi hỏng cả màn, luôn kèm hành động tiếp theo.
- **Màu loại hình:** 8 màu cố định theo `categoryIndex`, quay vòng khi nhiều hơn 8. Cùng một mẫu phải **cùng màu** ở preview bố trí và ở bảng số liệu. Bảng hiện tại: `#7c3aed #ea580c #0891b2 #ca8a04 #db2777 #16a34a #4f46e5 #b91c1c`.
- **Định dạng số:** dấu phân cách hàng nghìn kiểu Việt (`25.200`); cm bỏ số 0 thừa (`57` chứ không `57.0`; bản cũ hiện dấu chấm thập phân `0.3` — nên đổi sang dấu phẩy `0,3` cho thống nhất với kiểu Việt); m luôn 2 chữ số thập phân; % 1 chữ số.

### 5.2 Định tuyến

| Đường dẫn | Màn | Ghi chú |
|---|---|---|
| `/` | Trang chủ | Cửa ngõ duy nhất |
| `/nest/upload` | Bước 1 | |
| `/nest/settings` | Bước 2 | **Có canh gác**: chưa có hình nào → đẩy về bước 1 |
| `/nest/result/:jobId` | Bước 3 | Đọc lại job từ URL — tải lại trang / mở link lưu vẫn xem được |
| mọi đường khác | 404 | Có nút về trang chủ |

Chuyển màn thì cuộn lên đầu. Đây là SPA dùng history mode → host tĩnh phải rewrite mọi đường về `index.html`.

### 5.3 Trang chủ — "Công cụ sản xuất"

- Dòng phụ: "Chọn một chức năng để bắt đầu. Hiện tại chức năng ghép file đã sẵn sàng dùng."
- Lưới 5 ô, mỗi ô có icon, tiêu đề, mô tả:

| Ô | Mô tả | Trạng thái |
|---|---|---|
| Ghép file in | Tự dàn khuôn nhiều file lên khổ cuộn sao cho tốn ít mét nhất. | **Hoạt động** |
| Thư viện file | Lưu các mẫu in hay dùng để không phải tải lại mỗi lần. | Sắp có (khoá) |
| Lịch sử ghép | Xem lại các lần ghép trước và tải lại file đã xuất. | Sắp có |
| Preset khổ in | Lưu sẵn bộ tham số cho từng loại cuộn và từng máy in. | Sắp có |
| Báo cáo tiết kiệm | Thống kê số mét và số tiền đã tiết kiệm theo tháng. | Sắp có |

- Bấm "Ghép file in" → **xoá sạch đơn cũ** (danh sách, tham số về mặc định, job, dừng poll) → sang bước 1.

### 5.4 Bước 1 — "Nạp file in"

Dòng phụ: "Chọn các file cần in và khai báo số lượng mỗi loại." Có nút phụ "← Về trang chủ".

**Vùng kéo-thả**
- Tiêu đề "Kéo thả file in vào đây", gợi ý "Nhận file PDF, PNG, JPG — chọn được nhiều file cùng lúc.", nút "Chọn file". Nhận `.pdf .png .jpg .jpeg`.
- Tô nổi khi đang kéo file qua.
- Tải **lần lượt từng file, mỗi request một file**, hiện thanh % riêng từng file; file lỗi đánh dấu đỏ. Xong giữ thanh tiến độ ~1,2 giây rồi dọn.
- File lỗi: toast lỗi `"<tên file>: <message từ máy chủ>"`; các file khác vẫn vào bảng. Có file thành công: toast `"Đã nạp N file."`.
- Reset giá trị input sau mỗi lần chọn (chọn lại đúng file cũ vẫn kích hoạt).
- Thêm file mới thì **nối vào cuối** bảng; mặc định số lượng 1, cho xoay bật.

**Trạng thái rỗng:** "Chưa có file nào" — "Kéo thả file PDF, PNG hoặc JPG vào khung phía trên. App sẽ tự đọc kích thước thật của từng file; nếu đọc sai, bạn sửa lại trực tiếp trong bảng."

**Bảng hình** — cột: Xem trước · Tên file · Rộng (cm) · Dài (cm) · Số lượng · Cho xoay · (thao tác)
- **Xem trước:** ô ảnh nhỏ (~44 px). Bấm mở to giữa màn hình, chú thích `"W x H cm"`; nếu đã cắt trắng thêm `"· đã bỏ phần trắng bao quanh (khổ gốc W x H cm)"`. Ảnh lỗi tải (file đã bị dọn) thì ẩn ô.
- **Tên file:** cắt bớt nếu dài (tooltip tên đầy đủ) + badge `PDF` / `Ảnh` + badge "**đã cắt trắng**" (tooltip ghi khổ gốc) + nếu đã sửa kích thước: chữ nhỏ `"gốc W x H cm"`.
- **Rộng / Dài:** ô số sửa được (bước 0,1, tối thiểu 0,1). **Khoá tỷ lệ:** sửa một cạnh thì cạnh kia = cạnh vừa sửa × tỷ lệ của **kích thước gốc đọc từ file** (không phải giá trị đang hiện — tránh trôi sai số). Làm tròn 0,01 mm. Giá trị rỗng / ≤ 0 / không đọc được thì bỏ qua.
- **Số lượng:** số nguyên; rỗng hoặc < 1 thì thành 1.
- **Cho xoay:** công tắc, nhãn "Có" / "Không" với **chiều rộng cố định** (để bảng không bị đẩy ngang khi bật tắt).
- **Thao tác:** nút **hoàn tác** (chỉ hiện khi đã sửa kích thước; trả về gốc) và nút **xoá** dòng (tooltip `"Xoá <tên>"`).
- Ô số chấp nhận cả `2,5` và `2.5`. **Bẫy đã gặp:** dùng `<input type="number">` + v-model rồi xử lý như chuỗi → ô nhập âm thầm không đổi được. Dùng `type="text" inputmode="decimal"` và tự parse chuỗi thô.
- Cột số căn phải **cả tiêu đề lẫn ô**.

**Chân trang dính** (sticky đáy): `"Tổng: N loại hình · M bản · diện tích hình X cm²"` + nút chính "**Tiếp tục →**", khoá khi chưa có hình hoặc có kích thước ≤ 0.

### 5.5 Bước 2 — "Tham số in"

Dòng phụ: "Khai báo khổ cuộn và khoảng cách. Đây là những số quyết định số mét giấy phải dùng."

Bố cục hai cột: form bên trái, tóm tắt bên phải.

**Thẻ "Khổ in"** — "Kích thước cuộn giấy và giới hạn chiều dài mỗi file."

| Trường | Mặc định | Bước | Gợi ý dưới ô | Lỗi (hiện ngay dưới ô) |
|---|---|---|---|---|
| Khổ ngang (cm) | 57 | 0,5 | Chiều rộng cuộn giấy, đo thực tế không kể phần mép kẹp. | ≤ 0: "Khổ ngang phải lớn hơn 0." · > 1000: "Khổ ngang lớn bất thường, hãy kiểm tra lại đơn vị." |
| Lề biên (cm) | 0,5 | 0,1 | Chừa trống mỗi phía để máy in không ăn vào hình. | < 0: "Lề biên không được âm." · `2×lề ≥ khổ`: "Lề biên quá lớn so với khổ ngang." |
| Chiều dài tối đa mỗi file (cm) | **100** | 10 | Máy in yếu dễ bị lag với file quá dài; app sẽ tự chia thành nhiều file. Placeholder: "Để trống = không giới hạn". | Cho phép để trống (gửi `null`); giá trị ≤ 0 coi như trống. |

**Thẻ "Khoảng cách & xoay"** — "Ảnh hưởng trực tiếp tới số mét giấy tốn."

- **Khoảng cách giữa các hình (cm)** — mặc định **0,3**, bước 0,1, gợi ý "Khoảng hở tối thiểu để thợ có đường cắt.", lỗi "Khoảng cách không được âm."
- **Cách xếp** — nhóm radio, thứ tự từ an toàn tới tiết kiệm, mỗi lựa chọn có tiêu đề và giải thích:

| Giá trị | Tiêu đề | Giải thích hiển thị |
|---|---|---|
| `ORTHOGONAL` | Khung chữ nhật (mặc định) | Mỗi hình chiếm trọn khung chữ nhật, chỉ xoay 0 hoặc 90 độ. Bố trí thành lưới ngay ngắn, cắt kiểu nào cũng được. |
| `FREE` | Nhồi hình nhỏ vào góc trống | Hình nhỏ được chui LỌT HẲN vào phần trống bên trong khung của hình lớn. Chỉ dùng được khi cắt theo viền hình. |
| `TRUE_SHAPE` | Xếp lồng theo hình thật | Các hình lồng vào nhau theo đường nét thật, xoay nhiều góc kể cả góc chéo. CHỈ dùng khi cắt theo viền hình — cắt theo hình chữ nhật sẽ hỏng bản in. |

- **Cho phép xoay hình** — công tắc, mặc định bật. Gợi ý **đổi theo chế độ**:
  - Chế độ khác: "Tắt sẽ chặn xoay 90 độ ở mọi hình, ghi đè lên cột "Cho xoay" ở bước trước. Xoay được thường tiết kiệm thêm rất nhiều."
  - `TRUE_SHAPE`: "Tắt sẽ chặn TẤT CẢ các góc xoay, kể cả 45 độ — hình giữ nguyên chiều như trong file. Chỉ tắt khi hình buộc phải đúng chiều (chữ phải đọc ngang, canh sợi vải)."
- **Vẽ đường cắt quanh mỗi hình** — công tắc, mặc định **tắt**, gợi ý "Đường xám mờ 0,25pt, giúp thợ cắt nhanh hơn." (phần lớn đơn cắt theo viền nên mặc định tắt).

**Thẻ "Danh sách sẽ ghép"** (chỉ đọc) — phụ đề `"N loại hình"`; mỗi dòng: tên (cắt bớt), `W x H cm`, badge `xSL`, badge cảnh báo "khoá xoay" nếu dòng đó cấm xoay. Cuối thẻ: "Tổng bản in" và "Diện tích hình … cm²".

**Hàng nút:** "← Quay lại" (về bước 1, dữ liệu còn nguyên) · nút chính "**Ghép file**" (khoá khi form lỗi hoặc không có hình; trạng thái loading khi đang gửi).

**Khi bấm Ghép file:**
- Thành công → chuyển `/nest/result/<jobId>`, bắt đầu poll.
- Lỗi đồng bộ (vd. `ITEM_WIDER_THAN_SHEET`) → toast lỗi **và** ghi một job FAILED giả (`jobId = "local-error"`) để màn kết quả hiện lỗi ở **một chỗ duy nhất**.

### 5.6 Bước 3 — "Kết quả ghép"

Dòng phụ: "Kiểm tra bố trí rồi tải file PDF gửi máy in." (nên đổi thành "tải file TIF"). Nút phụ "← Về trang chủ".

**Nạp dữ liệu:** nếu store chưa có job này (vào thẳng URL / tải lại trang) thì gọi `GET job`; còn PENDING/RUNNING thì poll **700 ms**, dừng khi DONE/FAILED hoặc khi rời màn. Một lần poll lỗi mạng thì bỏ qua, poll tiếp. Không tìm thấy job → hiện lỗi "Không tìm thấy lần ghép này" / message máy chủ.

**Bốn trạng thái, không trạng thái nào trắng:**

1. **Đang đọc lại job:** spinner "Đang đọc kết quả…".
2. **Đang chạy:** spinner + "**Đang tính toán bố trí…**" + "App đang thử hàng trăm phương án xếp để tìm cách tốn ít giấy nhất. Việc này thường mất vài giây."
3. **Thất bại:** khối lỗi tiêu đề "**Không ghép được**", nội dung = `error.message`, nút chính "**Sửa tham số và thử lại**" → bước 2 (giữ nguyên dữ liệu).
4. **Xong:**

   **a. Bốn ô số:** Số file xuất ra (`file`) · **Tổng chiều dài** (`cm`, nổi bật) · Tỷ lệ lấp đầy (`%`) · **Tiết kiệm so với in rời** (`%`, màu thành công). 4 cột, màn hẹp thành 2 cột.

   **b. Bảng "Từng mẫu ăn mấy mét"** — phụ đề "Chiều dài thực tế đã tính cả phần giấy bỏ đi, chia đều theo tỷ lệ. Cộng lại đúng bằng tổng chiều dài."
   Cột: Xem trước (ảnh lấy theo `fileId`, chú thích `"W x H cm · N bản · X m"`) · Hình (chấm màu loại + tên) · Kích thước (cm, **trước xoay**) · Số bản · Diện tích (cm²) · Chiều dài (cm, 1 số lẻ) · Chiều dài (m, 2 số lẻ).
   Dòng **Tổng** in đậm ở cuối: tổng bản, tổng diện tích, tổng chiều dài cm và m (để thợ tự đối chiếu). Không có `byType` thì ẩn cả bảng.

   **c. Bảng "Chi tiết từng file"** — cột: STT · Kích thước (cm, `57 x 100`) · Số hình · Lấp đầy (%) · Tải về: **TIF** (nổi bật) · PDF · **CẮT** (tooltip "Đường cắt + 4 dấu định vị cho máy cắt Graphtec"). Link mở tab mới.

   **d. "Xem trước bố trí"** — phụ đề "Di chuột lên từng hình để xem tên file và kích thước." Nhiều tấm thì có tab "Tấm 1", "Tấm 2"…; kết quả mới về thì về tấm 1. Vẽ SVG đúng tỷ lệ:
   - Nền tấm, viền tấm vẽ sau cùng.
   - Thước cạnh trên và cạnh trái, vạch mỗi **10 cm**, số ghi bằng cm; thước dọc **đếm từ dưới lên** (đúng chiều bản in).
   - Mỗi bản: khối màu loại (nền nhạt ~18%, viền đậm), góc bo nhẹ; ghi `"WxH"` ở giữa nếu bản ≥ 45 × 22 mm, cỡ chữ co theo bản.
   - Tooltip theo chuột: tên file · `W x H cm` · `vị trí x ; y cm` · "Đã xoay 90 độ" / "Giữ nguyên hướng".
   - Tấm dài: giới hạn chiều cao khung (~70% màn hình) và cho cuộn.

   **e. Hàng nút cuối:** "Ghép lại với tham số khác" (về bước 2) · "Tải tất cả PDF" · "Tải tất cả file cắt" · **"Tải tất cả TIF"** (nút chính duy nhất, nhãn "đề xuất"). Thứ tự này có chủ ý: TIF là định dạng đưa vào máy in; file cắt là bước sau khi in.

**Chế độ demo:** bấm mọi link tải → chặn lại, toast info "Chế độ demo không xuất được PDF thật. Hãy chạy backend và tắt VITE_USE_MOCK để tải file."

### 5.7 Màn 404

"Không tìm thấy trang" + nút về trang chủ.

### 5.8 Chế độ demo (tuỳ chọn giữ lại)

Để bản frontend trên Render xem được giao diện khi backend tắt.
- Bật khi `VITE_USE_MOCK=true`; **để trống thì tự suy ra**: chưa khai `VITE_API_BASE_URL` → demo; đã khai → dùng backend thật.
- Thay toàn bộ tầng API bằng code chạy trên trình duyệt, **trả đúng schema thật**: ảnh đo bằng pixel ÷ 96 DPI; PDF dò `/MediaBox [...]` trong 64 KB đầu (không cắt trắng); thuật toán rút gọn (MaxRects BSSF, không tìm nhị phân, không leo đồi); ảnh xem trước là blob URL cục bộ; không xuất file.
- Header hiện badge "Chế độ demo". Trên bộ nghiệm thu đạt ~494,9 cm / 89,33%.
- Đổi schema API mà quên đồng bộ mock → bản demo hỏng âm thầm (hiện `undefined`/`NaN`). Nếu giữ mock, phải có test chạy mock với bộ nghiệm thu.

---

## 6. Hợp đồng API

Tiền tố `/api/v1`. Mọi số đo là **mm**. Giữ đúng hợp đồng này thì frontend cũ / mới cắm được nhau.

**Thân lỗi thống nhất** (mọi lỗi, mọi endpoint):

```json
{ "error": { "code": "MA_LOI", "message": "Thông điệp tiếng Việt hiển thị thẳng cho người dùng", "details": {} } }
```

Frontend phân nhánh theo `code`, **không parse `message`**. Lỗi không có phản hồi → client tự dựng `NETWORK_ERROR` ("Không kết nối được tới máy chủ. Hãy kiểm tra backend đã chạy chưa…"); mã HTTP lạ không có thân chuẩn → `INTERNAL_ERROR` "Máy chủ trả về lỗi <status>." Timeout client 120 s.

### 6.1 `POST /files` — tải file

`multipart/form-data`, trường `files` (lặp được; frontend gửi mỗi lần một file). Trả `200`:

```json
[{
  "id": "4f2c8a91-…",
  "originalName": "nhan-ao.pdf",
  "widthMm": 30.0,  "heightMm": 30.0,
  "sourceWidthMm": 210.0, "sourceHeightMm": 297.0,
  "trimmed": true,
  "type": "PDF",
  "pageCount": 1,
  "previewUrl": "/api/v1/files/4f2c8a91-…/preview"
}]
```

- `widthMm/heightMm`: kích thước **dùng để xếp** (PDF đã cắt trắng). `sourceWidthMm/sourceHeightMm`: khổ trang gốc. `trimmed`: có cắt được gì không. `type`: `PDF` | `IMAGE`.
- `previewUrl` là đường dẫn tương đối — frontend ghép với base URL.
- Lỗi: `UNSUPPORTED_FORMAT` 415 (đuôi khác .pdf/.png/.jpg/.jpeg) · `FILE_TOO_LARGE` 413 (> 50 MB/file; request tối đa 200 MB) · `SIZE_UNREADABLE` 422 (gợi ý nhập tay) · `INVALID_REQUEST` 400 ("Chưa chọn file nào để tải lên").

### 6.2 `GET /files/{id}/preview` — ảnh xem trước

- Ảnh đã cắt đúng content box (khớp `widthMm/heightMm`), cạnh dài tối đa **360 px**, giữ trong RAM.
- PDF → `image/png`. Cách cắt: đặt CropBox của trang = content box rồi render (DPI tự tính trong khoảng 24–200), để `/Rotate` được xử lý đúng một lần.
- Ảnh → `image/jpeg` chất lượng 0,82, **không cắt** viền; giải nén subsampling với ảnh lớn.
- Chỉ phục vụ hiển thị — xuất file luôn dùng file gốc. Lỗi `FILE_NOT_FOUND` 404.

### 6.3 `POST /nesting/jobs` — tạo lần ghép

```json
{
  "mode": "ORTHOGONAL",
  "sheetWidthMm": 570,
  "marginMm": 5,
  "gapMm": 3,
  "maxSheetLengthMm": 1000,
  "allowRotateGlobal": true,
  "drawCutLines": false,
  "items": [
    { "fileId": "…", "quantity": 50, "allowRotate": true, "widthMm": 200, "heightMm": 180 }
  ]
}
```

| Trường | Bắt buộc | Kiểm tra | Ý nghĩa |
|---|---|---|---|
| `mode` | | `ORTHOGONAL` \| `FREE` \| `TRUE_SHAPE` | Thiếu = `ORTHOGONAL` |
| `sheetWidthMm` | ✔ | > 0 | Khổ ngang |
| `marginMm` | ✔ | ≥ 0 | Lề mỗi phía |
| `gapMm` | ✔ | ≥ 0 | Khoảng cách tối thiểu |
| `maxSheetLengthMm` | | > 0 hoặc `null` | `null` = không giới hạn |
| `allowRotateGlobal` | ✔ | | Tắt ghi đè mọi dòng |
| `drawCutLines` | | | Thiếu = `true` (frontend luôn gửi, mặc định `false`) |
| `items` | ✔ | không rỗng | |
| `items[].fileId` | ✔ | không trống | |
| `items[].quantity` | ✔ | ≥ 1 | |
| `items[].allowRotate` | ✔ | | |
| `items[].widthMm/heightMm` | | > 0 | Ghi đè kích thước từ file; thiếu thì dùng metadata |

**Kiểm tra đồng bộ trước khi tạo job** (trả lỗi ngay, không tạo job):
- `fileId` không tồn tại → `FILE_NOT_FOUND`.
- Bản có cạnh hẹp nhất (tính cả xoay nếu được phép) > `khổ − 2 × lề` → `ITEM_WIDER_THAN_SHEET` 422, `details: { fileId, label }`, message dạng `Hình "bang-ron.pdf" 60x20 cm rộng hơn khổ 57 cm (đã tính cả trường hợp xoay).` hoặc `… và không được phép xoay.`
- Sai ràng buộc → `INVALID_REQUEST` 400 kèm message của trường đầu tiên sai.

Hợp lệ → `202`:

```json
{ "jobId": "a7e3f019-…", "status": "PENDING", "progress": 0, "createdAt": "2026-09-19T13:42:07.881Z" }
```

**Lỗi do engine phát hiện** (đến qua job `FAILED` khi poll, không phải lúc tạo):
- Lề quá lớn, không còn vùng xếp → `INVALID_REQUEST` "Lề biên quá lớn so với khổ ngang: không còn vùng nào để xếp hình."
- `maxSheetLengthMm` nhỏ hơn bản cao nhất → `INVALID_REQUEST` "Chiều dài tối đa mỗi file (X cm) nhỏ hơn hình cao nhất (Y cm). Hãy tăng giới hạn chiều dài."
- Vượt 500 tấm (200 ở TRUE_SHAPE) → `INVALID_REQUEST` "Số tấm vượt quá giới hạn an toàn…"
- Lỗi không lường → `NESTING_FAILED` "Thuật toán gặp lỗi không mong muốn: …"

Máy chủ **ghi log đúng tham số nhận được** (chế độ, khổ, lề, gap, dài tối đa, xoay, số loại, số bản) — để trả lời ngay câu "giao diện có gửi giá trị mới lên không".

### 6.4 `GET /nesting/jobs/{jobId}` — trạng thái

Luôn `200` nếu job tồn tại:

```json
{
  "jobId": "…", "status": "DONE", "progress": 100,
  "createdAt": "…", "finishedAt": "…",
  "result": { "sheets": [ … ], "stats": { … } }
}
```

- `status`: `PENDING` (progress 0) → `RUNNING` (progress 10) → `DONE` (100) hoặc `FAILED` (kèm `error`, cùng cấu trúc thân lỗi).
- Không có job → `JOB_NOT_FOUND` 404, message "Không tìm thấy lần ghép <id>. Có thể máy chủ đã khởi động lại."
- Tham số gốc của job **không** trả ra ngoài (job được poll mỗi 700 ms — không gửi thừa).

**`result.sheets[]`:**

```json
{
  "index": 0, "widthMm": 570.0, "lengthMm": 4782.0, "fillRate": 0.9245,
  "placements": [{
    "fileId": "…", "label": "nhan-ao.pdf", "categoryIndex": 0,
    "xMm": 5.0, "yMm": 5.0, "wMm": 200.0, "hMm": 180.0,
    "rotated": false, "angleDeg": 0, "sourceWMm": 200.0, "sourceHMm": 180.0
  }]
}
```

- **Gốc toạ độ ở góc TRÁI-DƯỚI tấm** (quy ước PDF); `xMm, yMm` là góc trái-dưới **khung bao** của bản. Vẽ SVG: `ySvg = lengthMm − yMm − hMm`.
- `wMm/hMm` là khung bao **sau khi xoay** — không tự hoán đổi.
- `rotated`: xoay đúng 90°. `angleDeg`: góc xoay tự do (độ, ngược chiều kim đồng hồ), chỉ khác 0 ở `TRUE_SHAPE`, lúc đó `rotated = false`.
- `sourceWMm/sourceHMm`: kích thước **trước khi xoay** — bắt buộc để vẽ bản xoay chéo đúng tỷ lệ.
- `fillRate`: diện tích **hợp** các khung bao / diện tích tấm, 4 chữ số thập phân.

**`result.stats`:**

```json
{
  "totalSheets": 1,
  "totalLengthMm": 4782.0,
  "totalShapeAreaMm2": 2520000.0,
  "usedAreaMm2": 2725740.0,
  "fillRate": 0.9245,
  "savedVsIndividualPct": 71.9,
  "totalPieces": 145,
  "byType": [{
    "fileId": "…", "label": "nhan-20x18.pdf", "categoryIndex": 5,
    "pieces": 50, "widthMm": 200.0, "heightMm": 180.0,
    "shapeAreaMm2": 1800000.0, "lengthMm": 3415.7
  }]
}
```

Công thức ở mục 9.

### 6.5 Tải file thành phẩm

| Endpoint | Content-Type | Tên file tải về |
|---|---|---|
| `GET /nesting/jobs/{id}/sheets/{i}/pdf` | `application/pdf` | `minh-tri-<8 ký tự đầu jobId>-tam01-57x100cm.pdf` |
| `GET /nesting/jobs/{id}/sheets/{i}/tif` | `image/tiff` | `minh-tri-<id8>-tam01-57x100cm.tif` |
| `GET /nesting/jobs/{id}/sheets/{i}/cut` | `application/pdf` | `minh-tri-<id8>-tam01-57x100cm-cat.pdf` |
| `GET /nesting/jobs/{id}/export.zip?format=pdf\|tif\|cut` | `application/octet-stream` | `minh-tri-<id8>-<format>.zip` |

- `i` bắt đầu từ 0; trong tên file đánh số từ `01`. Kích thước trong tên làm tròn cm. File cắt **bắt buộc** có hậu tố `-cat` để không nhầm với bản in khi nằm cạnh nhau.
- `Content-Disposition: attachment`.
- `format` lạ → `INVALID_REQUEST` "Định dạng "x" không hỗ trợ. Chỉ nhận pdf, tif hoặc cut." (không âm thầm trả PDF).
- Lỗi: `JOB_NOT_FOUND` (kể cả chỉ số tấm ngoài khoảng: "Lần ghép này chỉ có N tấm, không có tấm số K.") · `JOB_NOT_READY` 409 ("Lần ghép này chưa có kết quả (trạng thái: …)") · `TIFF_TOO_LARGE` 422.

**Zip:**
- **Ghi thẳng ra luồng phản hồi**, dựng xong tấm nào ghi và flush tấm đó — chỉ một tấm trong bộ nhớ một lúc.
- Tấm nào dựng hỏng giữa chừng → ghi file `LOI-tamNN.txt` chứa lý do vào zip rồi làm tiếp. Không được để zip đứt giữa chừng (zip cụt vẫn tải về được và mở được vài file đầu → thợ không biết là thiếu).
- Thời hạn phản hồi phải đủ dài (bản cũ 30 phút) — timeout mặc định 30 s từng cắt cụt zip.
- Log tổng kết: số tấm, số tấm hỏng, thời gian.

**Cache file đã dựng:** theo khoá `(jobId, chỉ số tấm, định dạng)`, LRU, trần `min(200 MB, heap/8)`. Tải lẻ một tấm → lưu cache. Tải zip → **đọc** cache nếu có nhưng **không ghi thêm** (ghi thêm từng làm hết RAM ở tấm thứ 9).

### 6.6 Bảng mã lỗi

| Mã | HTTP | Khi nào |
|---|---|---|
| `FILE_TOO_LARGE` | 413 | File vượt giới hạn dung lượng |
| `UNSUPPORTED_FORMAT` | 415 | Đuôi file không hỗ trợ |
| `SIZE_UNREADABLE` | 422 | Không đọc được kích thước vật lý |
| `ITEM_WIDER_THAN_SHEET` | 422 | Có bản rộng hơn khổ, kể cả khi xoay |
| `INVALID_REQUEST` | 400 | Tham số sai, lề quá lớn, giới hạn dài < bản cao nhất, định dạng tải lạ… |
| `JOB_NOT_FOUND` | 404 | Không có job — thường do máy chủ khởi động lại |
| `FILE_NOT_FOUND` | 404 | Không có file |
| `JOB_NOT_READY` | 409 | Job chưa xong |
| `NESTING_FAILED` | 500 | Thuật toán lỗi hoặc lệch số lượng |
| `TIFF_TOO_LARGE` | 422 | Tấm quá lớn để dựng TIF với RAM hiện có; message nêu DPI nên đặt |
| `INTERNAL_ERROR` | 500 | Lỗi không lường; stack trace chỉ vào log |

---

## 7. Đọc file nguồn (upload)

- Lưu file xuống đĩa, tên = UUID + đuôi gốc. Metadata giữ trong RAM.

### 7.1 PDF

1. Chỉ dùng **trang đầu**. `pageCount` báo số trang.
2. **Tìm content box bằng cách đọc content stream** (vector), **không quét pixel** — hình tô màu trắng vẫn là nét vẽ phải giữ. Gộp mọi lệnh vẽ (đường, hình tô, ảnh nhúng, chữ) vào một hộp bao chung, nên nhiều hình nhỏ trong một file vẫn giữ vị trí tương đối. Nét vẽ có độ dày được nới thêm nửa độ dày. Cắt hộp vào trong trang. Giới hạn 50.000 hình để không treo với file quá phức tạp.
3. Không đọc được, trang trắng, hoặc hộp < 1 mm một cạnh → lùi về **TrimBox → CropBox → MediaBox**.
4. Trang có `/Rotate` 90 / 270 → hoán đổi W/H báo ra ngoài.
5. Lưu `contentBox` (point, hệ toạ độ trang gốc) và `pageRotation` cùng file. **Bước xuất bắt buộc dùng đúng hộp này.**
6. Dựng thêm **bản đồ chiếm chỗ (occupancy mask)**: lưới ô **0,5 mm** trong content box, bit = ô có nét vẽ. Dùng cho chế độ FREE và TRUE_SHAPE. Ảnh không có bản đồ.
7. Tắt cắt trắng bằng cấu hình → lấy trọn hộp trang.

### 7.2 Ảnh PNG / JPG

- **Không cắt trắng** (viền trắng của decal có thể là cố ý).
- `mm = pixel × 25,4 / DPI`, DPI đọc từ metadata (`HorizontalPixelSize` là mm/pixel → DPI = 25,4 ÷ giá trị). Không có DPI → **72 DPI**. Ví dụ PNG 600×400 khai 300 DPI → **50,8 × 33,87 mm**.
- Ghi nhận ảnh **có kênh trong suốt hay không**, chỉ đọc khai báo kiểu ảnh, không giải nén. Không xác định được → coi như **có** (thiên về giữ mực trắng — xem 10.3).

---

## 8. Thuật toán xếp

Bài toán **strip packing**: bề rộng cố định, tối thiểu hoá chiều dài. Mọi tính toán trong engine là **số nguyên 1/100 mm**.

### 8.1 Chuẩn bị chung

- Kích thước mỗi dòng = giá trị người dùng gửi; thiếu thì lấy từ file.
- `categoryIndex` gán theo thứ tự xuất hiện của `fileId` trong `items`.
- Được xoay = `allowRotateGlobal && item.allowRotate`.
- **Nở mỗi bản thêm trọn một gap** ở cả hai chiều. Khi ghi toạ độ thật, neo bản vào góc trái-dưới của ô đã nở → mọi bản dịch như nhau → khoảng hở đúng bằng gap.
- **Bề rộng khả dụng = `khổ − 2×lề + gap`** (bản sát biên không cần chừa gap phía ngoài; riêng điều này đáng 2,4 cm trên bộ nghiệm thu). Chiều dài: tương tự cắt bớt đúng một gap ở đầu trên.
- Giới hạn chiều dài: giữ hai con số tách biệt — chiều dài **nội dung thật** và chiều dài **đã cộng gap** cho packer. Dùng chung một số từng làm tấm dài hơn giới hạn đúng một gap (đặt 200 cm, gap 5 cm → ra 205 cm). Không cộng thêm hàng "cho chắc".

### 8.2 Chế độ ORTHOGONAL (mặc định)

1. **Phương án ép hướng:** ngoài phương án "tự do" (packer tự quyết xoay từng bản), duyệt các phương án **chốt một hướng cho cả loại** — 2ⁿ phương án với n loại xoay được. Chỉ duyệt khi n ≤ 8. Cải tiến lớn nhất: +0,9 điểm % lấp đầy (các bản cùng loại xếp thành cột đều, dải thừa gom liền nhau).
2. Mỗi phương án × **6 thứ tự sắp**: diện tích ↓, cạnh dài ↓, cạnh ngắn ↓, chiều cao ↓, chiều rộng ↓, chu vi ↓ (tie-break theo id).
3. × **5 packer**: MaxRects với 4 heuristic — **BSSF** (cạnh ngắn khít nhất), **BLSF** (cạnh dài khít nhất), **BAF** (diện tích thừa ít nhất), **BL** (thấp nhất rồi trái nhất) — và một **Shelf packer** đối chứng. Mọi so sánh điểm có tie-break đầy đủ `(y, x, hướng)`.
4. Mỗi tổ hợp: **tìm nhị phân** chiều dài L nhỏ nhất còn xếp hết (tối đa 40 bước). Cận dưới = diện tích lý thuyết; cận trên = một lần xếp không ràng buộc. Ép chiều dài buộc packer nén chặt.
5. Trong mỗi lần xếp, bản không nhét được **không dừng vòng lặp**: gom lại, thử lại theo diện tích **tăng dần**, lặp tới khi hết tiến triển (lấp dải biên bằng bản nhỏ).
6. **Leo đồi:** từ phương án tốt nhất của **từng heuristic**, xáo nhẹ thứ tự (đưa các bản nằm ở dải trên cùng ~12% chiều dài lên trước), xếp lại, giữ nếu ngắn hơn — **300 vòng × 3 hạt gieo cố định** (`20240917, 7, 1337`).
7. Chọn kết quả ngắn nhất.
8. Tối ưu cần thiết của MaxRects: khi cắt danh sách ô trống, chỉ đối chiếu các mảnh **mới sinh** (prune O(n²) mỗi lần đặt chậm hàng chục lần).

### 8.3 Chia nhiều tấm (`maxSheetLengthMm`)

- Đổ bản vào tấm hiện tại tới khi đầy, chọn phương án đặt được **nhiều bản nhất**; chốt tấm, mở tấm mới với các bản còn lại.
- Biết tập bản của một tấm rồi thì **xếp lại tấm đó** một lần nữa cho sát → tấm cuối không dài vô ích.
- Tối đa 500 tấm (an toàn).
- Kiểm tra số lượng trước khi trả: lệch một bản → `NESTING_FAILED`.

### 8.4 Chế độ FREE — nhồi vào hốc trống

- Với mỗi mẫu có bản đồ chiếm chỗ: **nở vùng đặc ra một gap**, rồi tìm tối đa **4** hình chữ nhật trống lớn nhất bên trong khung bao (thuật toán "hình chữ nhật lớn nhất trong biểu đồ cột" chạy theo hàng, lặp: tìm, đánh dấu đã dùng, tìm tiếp). Bỏ hốc có cạnh < **8 mm**.
- Các hốc trả về packer như vùng trống thêm. Bản nào nằm **lọt hẳn** trong hốc thì tự đủ khoảng hở với nét vẽ của mẫu chủ.
- Chỉ bật hốc khi đủ **cả ba**: có bản đồ (PDF vector); kích thước **không bị sửa tay** (sai lệch ≤ 0,01 mm); trang **không** có `/Rotate`. Thiếu → mẫu là khối đặc (kém hơn nhưng không bao giờ sai).
- Xoay hốc theo chiều cao **thật** của mẫu, không phải chiều cao đã nở gap (lệch một gap → bản chui vào chạm nét vẽ).
- Cận dưới tìm nhị phân dùng **diện tích ròng** (đã trừ hốc), nếu không tự chặn mất phần lợi.

### 8.5 Chế độ TRUE_SHAPE — xếp theo hình thật

- Mỗi mẫu mang **mặt nạ bit lưới 1 mm**, lấy mẫu lại từ bản đồ 0,5 mm, **làm tròn ra ngoài** (ô chạm vùng đặc là đặc — sai về phía đặc chỉ kém chút, sai về phía trống là hai hình đè nhau). Mỗi hàng là dãy bit trong mảng `long`; va chạm = phép AND 64 ô một lúc.
- Tấm là một lưới bit. Bản đã đặt in lên lưới bằng mặt nạ **đã nở gap**; bản mới đo bằng mặt nạ **chưa nở** → không đụng bit nào là đã cách ≥ gap.
- Góc thử: `0, 90, 180, 270, 45, 135, 225, 315` (tắt xoay → chỉ 0°). Mặt nạ xoay góc vuông thì hoán vị chính xác; góc chéo thì xoay và làm tròn ra ngoài.
- Với mỗi góc, quét từng cột, tìm vị trí **thấp nhất** đặt được (thả rơi từ trên xuống); chọn vị trí có **đỉnh** bản thấp nhất, hoà thì trái nhất.
- Kích thước báo ra (`wMm/hMm`) tính bằng **lượng giác thật** từ khung bao và góc — không lấy số ô × cạnh ô (làm tròn sẽ kéo giãn hình).
- Điều kiện dùng mặt nạ giống FREE; không đủ → khung bao đặc.
- Tối đa 200 tấm. Giới hạn biết trước: không chui được vào hốc kín hoặc hốc chỉ mở ngang.

### 8.6 Nghiệm thu thuật toán

Bộ thật của xưởng — khổ 57 cm, gap 0,3 cm, lề 0,5 cm, cho xoay, `ORTHOGONAL`:

| Kích thước (cm) | 7×3 | 5×7 | 13×4 | 4×17 | 16×13 | 20×18 | **Tổng** |
|---|---|---|---|---|---|---|---|
| Số lượng | 30 | 10 | 20 | 15 | 20 | 50 | **145 bản · 25.200 cm²** |

| Kịch bản | Số tấm | Tổng dài | Lấp đầy | Tiết kiệm | Thời gian |
|---|---|---|---|---|---|
| Không giới hạn dài | 1 | **478,2 cm** | **92,45 %** | **71,9 %** | ~5 s |
| Giới hạn 120 cm | 5 | 494,1 cm | 89,48 % | — | ~6 s |

Bản mới phải **bằng hoặc tốt hơn**. Cận dưới lý thuyết 466,8 cm (94,6 %). Các hướng đã thử **không** cải thiện: ép hướng "mềm", tăng vòng leo đồi lên 1.200, nhiều hạt gieo hơn. Hướng có thể vượt: xếp theo khối k×m, hoặc quy hoạch nguyên.

File mẫu trong `samples/` (sinh bằng `samples/generate.mjs`, MediaBox/TrimBox đặt chính xác theo tên file) và kịch bản kỳ vọng trong `samples/README.md`.

---

## 9. Số liệu kết quả

| Số | Công thức |
|---|---|
| `fillRate` (tấm) | diện tích **hợp** các khung bao trên tấm / (khổ × dài tấm). Hợp tính chính xác bằng nén toạ độ, không xấp xỉ lưới. |
| `totalLengthMm` | tổng chiều dài các tấm |
| `totalShapeAreaMm2` | Σ w × h × số lượng (kích thước bản, chưa nở gap) |
| `usedAreaMm2` | Σ khổ × dài của các tấm |
| `fillRate` (tổng) | hợp các khung bao mọi tấm / `usedAreaMm2` |
| `savedVsIndividualPct` | `(1 − totalLength / dài khi in rời) × 100`, in rời = mỗi bản chiếm trọn bề ngang khổ |
| `totalPieces` | tổng số bản |
| `byType[].lengthMm` | `totalLengthMm × (shapeArea của mẫu / tổng shapeArea)` |

**Về `fillRate`:** dùng **hợp**, không phải tổng — ở chế độ lồng, khung bao chồng nhau và cộng tổng từng ra "lấp đầy 100,3 %, giấy bỏ đi −0,3 %". Chế độ lưới không có chồng nên hợp = tổng, số cũ không đổi.

**Về chia chiều dài từng mẫu (`byType`):**
- Chia theo **diện tích riêng của từng bản**, **không** theo phần giấy mẫu đó chiếm chỗ riêng. Cách sau từng cho mẫu 8 × 0,9 cm, 11 bản, 83 cm² giấy ghi **0 cm** (chui gọn trong khung mẫu khác) → khách được miễn phí giấy, sai tiền thật.
- Phần giấy bỏ đi được chia đều theo tỷ lệ — không mẫu nào một mình gây ra chỗ trống.
- Xử lý làm tròn để **tổng các dòng đúng bằng `totalLengthMm`**.
- `widthMm/heightMm` của dòng là kích thước **trước xoay**. Sắp theo `categoryIndex` tăng dần.

---

## 10. Xuất file

### 10.1 PDF (bản in vector)

- Trang đúng `sheetWidthMm × lengthMm`, đổi sang point.
- **PDF nguồn:** nhúng **trang 1** thành form XObject (PDFBox `LayerUtility.importPageAsForm`) — giữ vector và chữ.
- **Ảnh nguồn:** image XObject đặt đúng kích thước vật lý.
- Mỗi file nguồn **chỉ nhúng một lần** rồi dùng lại cho mọi bản (cache theo fileId) — không thì PDF phình gấp nhiều lần.
- Ma trận cho mỗi bản, ghép theo thứ tự:
  1. `translate(x, y)` tới vị trí đặt.
  2. Nếu `rotated` (90°): `translate(w, 0)` rồi `rotate(90°)` — để bản rơi đúng vào ô.
     Nếu `angleDeg ≠ 0` (TRUE_SHAPE): tính 4 góc khung sau khi xoay, `translate(−minX, −minY)` rồi `rotate(angle)` — góc trái-dưới của khung đã xoay nằm đúng (0,0) của ô.
  3. `scale(sx, sy)` về đúng kích thước đích (tính từ `sourceWMm/sourceHMm` và content box; với kích thước giữ nguyên thì tỷ lệ = 1).
  4. Bù cờ `/Rotate` của trang nguồn (cờ này mất tác dụng khi nhúng sang trang khác).
  5. `translate(−box.x, −box.y)` đưa góc **content box** về 0 — vừa bù gốc hộp, vừa bù phần lề đã cắt trắng.
- **Không** dùng `form.getBBox()` (luôn là CropBox) — phải dùng content box đã lưu lúc upload.
- `drawCutLines = true` → khung xám 0,25 pt quanh mỗi bản.
- Cung cấp thêm hàm khoanh **vùng các bản đến từ ảnh không có kênh trong suốt** (theo toạ độ pixel ở DPI cho trước) — dùng ở 10.3.

### 10.2 TIF — thông số file

Raster hoá **từ chính bản PDF ở 10.1** (với cùng tuỳ chọn `drawCutLines` của job), mặc định **300 DPI**. Mọi thông số dưới đây đã đối chiếu với **file mẫu thợ làm tay trong Photoshop** (`samples/IN6 2209.tif`).

| Tag | Giá trị | Vì sao |
|---|---|---|
| Compression | Deflate (mức nén 0,3); cấu hình đổi được sang LZW | Không mất dữ liệu |
| PhotometricInterpretation | 5 = CMYK, 8 bit/kênh | Hệ màu RIP nhận |
| SamplesPerPixel | 5 = C, M, Y, K, W1 | Kênh 5 là mực trắng |
| ExtraSamples | **0** (không xác định) | Để 2 thì phần mềm hiểu W1 là độ trong suốt |
| Photoshop (34377) | Khối khai kênh `W1` là **spot color**, solidity 100% | TIFF chuẩn không có chỗ ghi tên kênh. Khối này **luôn** big-endian |
| ImageSourceData (37724) | Một lớp Photoshop có độ trong suốt (10.5) | Theo byte order của file TIFF bao quanh |
| XResolution / YResolution | = DPI | **Thiếu là in sai kích thước** (phần mềm đoán 72 DPI, tấm 57 cm thành 2,4 m) |
| ResolutionUnit | 2 (inch) | |
| RowsPerStrip | 64 | Mặc định Java là 1 → file to gấp ~5 |
| ICCProfile | hồ sơ CMYK dùng để chuyển màu | RIP biết đúng nghĩa các con số |

- Ghi metadata thất bại → **từ chối phát hành file** (lỗi), không phát hành file thiếu độ phân giải.
- Tag chứa byte thô ghi bằng `TIFFDirectory` / `TIFFField` (qua cây metadata dạng chuỗi thì khối 20 MB thành 136 MB RAM).
- Chế độ `rgb` (tuỳ chọn cấu hình): RGB + sRGB, không có W1.

### 10.3 TIF — màu và kênh mực trắng W1

**Màu:**
- Chuyển CMYK bằng **ICC thật** (`ColorConvertOp`), hồ sơ mặc định **U.S. Web Coated (SWOP) v2** (đúng hồ sơ trong file mẫu, 557 KB, nằm ở `backend/src/main/resources/color/`). Không dùng công thức tay `K = 1 − max(R,G,B)`. Cho cấu hình hồ sơ khác (hồ sơ của máy in).
- **Trải ảnh lên nền trắng trước khi chuyển màu** — đừng để bộ chuyển tự xử lý alpha (cùng điểm `0x00000000` cho kết quả khác nhau tuỳ kích thước ảnh, có thể đổ đen khoảng trống).
- Chuyển vào ảnh 4 kênh liền mạch rồi **chép** sang mảng 5 kênh (ColorConvertOp bỏ qua stride → ảnh sọc ngang).

**Mặt nạ "có hình":**
1. Từ **kênh alpha** của ảnh render: alpha > 128 (cấu hình) → có hình.
2. **Ngoại lệ cho ảnh dẹt** (JPG, PNG không có alpha — nền trắng là pixel đặc): trong **vùng của những bản đó**, loang từ mép vùng / mép tấm vào qua các điểm gần trắng (dung sai 6 so với 255) và các điểm trong suốt — giống bấm magic wand vào nền — và coi là nền. Chữ trắng nằm giữa logo được màu bao quanh nên loang không tới → vẫn giữ.
3. **Không áp phép loang cho PDF và PNG có alpha:** ở đó độ trong suốt đã nói chỗ nào không in, nên màu trắng còn lại là **nét vẽ có chủ ý**. (Lỗi thật: logo có dòng "BÒ TƯƠI" và hotline chữ trắng ra TIF không có **một điểm mực nào** — in lên áo tối là mất chữ.)
4. Chỗ vẫn chịu: ảnh dẹt có viền trắng chạm mép ảnh → bị coi là nền.

**Kênh W1:**
- = mặt nạ "có hình" **co vào trong 1 px** mỗi phía (cấu hình; bằng thao tác `Select → Modify → Contract 1` của thợ; 1 px @300 DPI = 0,085 mm) để lớp trắng nằm lọt trong lớp màu.
- **Chiều giá trị ngược:** trong file, `W1 = 0` là **có** mực trắng, `255` là không (cách Photoshop ghi spot channel). Đặt ngược → lớp trắng âm bản, chỉ lộ khi mực lên áo.

### 10.4 TIF — lớp trong suốt (ô caro trong Photoshop)

Ảnh gộp TIFF không có khái niệm trong suốt: chỗ không mực là CMYK `0 0 0 0`, Photoshop vẽ thành **trắng**. Thợ cần thấy **ô caro** để biết máy chỉ in phần chi tiết. Nên file mang thêm một lớp ở tag 37724:

```
Chữ ký   "Adobe Photoshop Document Data Block"
Khối "Layr":
  1 lớp, khung (0,0)–(rộng, cao) phủ kín tấm
  5 kênh: −1 (trong suốt), 0 C, 1 M, 2 Y, 3 K
  blend "norm", opacity 255, tên "Layer 1"
  nén ZIP (mặc định) hoặc RLE như file mẫu — cấu hình
```

- Bốn kênh màu của **lớp** lưu **lật** (`255 − giá trị`; PSD: 0 = mực đầy). Kênh trong suốt **không** lật. (Không lật → màu kem đọc thành đen đặc, mà ảnh gộp vẫn đúng nên test số liệu không bắt được.)
- Kênh trong suốt = mặt nạ "có hình" **chưa co** (lớp màu trải hết ra mép; chỉ W1 mới thụt vào).
- Đọc / ghi chú ý byte order: khối 34377 luôn big-endian; khối 37724 theo byte order của file (file mẫu `II` → chữ ký đọc ra `"MIB8"`, `"ryaL"`).
- Cấu hình tắt được lớp này (ảnh gộp không đổi, chỉ mất ô caro).

**Kiểm tra trong Photoshop:** nền ô caro; bảng Layers có `Layer 1`; Channels có đúng 5 kênh Cyan, Magenta, Yellow, Black, **W1**; bấm đúp W1 ghi **Spot Color**, Solidity 100 %; vùng đen của W1 khớp hình nhưng nhỏ hơn mép 1 px.

### 10.5 File CẮT (Graphtec CE7000-60 / Cutting Master)

PDF vector riêng, **không phải bản in**. Thợ in TIF ra phim, mở file cắt trong Illustrator / CorelDRAW, gửi máy cắt qua Cutting Master.

**Dựng đường cắt:**
1. Dựng PDF của tấm với khung cắt xám **luôn tắt** (bật thì đường cắt đi quanh khung xám).
2. Render ở **300 DPI** (cấu hình), dựng **đúng mặt nạ "có hình" của 10.3, CHƯA co** — dùng chung hàm và tham số với TIF (một chỗ định nghĩa). Dùng bản đã co → nét mảnh đứt thành chấm 1 px rồi bị lọc mất (lỗi thật: dòng chữ nhỏ "NGUYÊN BẢN TRÀ VIỆT" vỡ thành 59 chấm và biến mất).
3. Nở ra ngoài `offsetMm` (mặc định **0** = chạy đúng trên viền W1) — nở **trên mặt nạ** (dilate), không polygon-offset. Hai hình sát nhau tự dính thành một khối.
4. Dò viền: lấy **cả viền ngoài lẫn viền lỗ bên trong** (ruột chữ o, a, ê được khoét — giống stroke selection quanh layer trong Photoshop).
5. Bỏ mảng < **0,2 mm²** — chỉ để lọc hạt bụi (1 px @300 DPI = 0,007 mm²). Ngưỡng cao hơn sẽ ăn chữ nhỏ thật.
6. Rút gọn đỉnh: **gộp các đỉnh thẳng hàng trước** (O(n)), rồi Douglas–Peucker sai số **0,1 mm**. Đưa thẳng vào DP thì treo máy (viền theo cạnh pixel có hàng trăm nghìn đỉnh).

**Cấu trúc PDF:**

| | |
|---|---|
| Bề ngang trang | **Đúng bằng bản in**, lấy từ dữ liệu tấm, không quy ngược từ số pixel |
| Chiều dài trang | Bằng bản in, cộng phần nới nếu góc chật (dưới đây) |
| Lớp (OCG) `CutContour` | Đường cắt, nét 0,25 pt, colorspace Separation tên **`CutContour`** (alternate K100), không tô |
| Lớp (OCG) `RegMarks` | 4 dấu định vị tô đặc K100 |
| Dấu định vị | Cạnh **15 mm**, dày **1 mm**, góc ngoài đặt **đúng 4 góc trang** |
| Kiểu dấu | Khai **riêng từng góc**: `SQUARE_WITH_EDGE` (mặc định — chỉ vẽ 2 nét ở hai cạnh phía trong, 2 cạnh còn lại là mép giấy), `L` (hai cạnh ốp vào mép), `SQUARE_OUTLINE`, `SQUARE_FILLED` |

**Góc chật thì nới trang DÀI ra:**
- Mỗi góc cần vùng sạch **20 mm** (dấu 15 + 5 mm trống). Có mực lấn vào → camera đo nhầm dấu.
- Khi đó **nới trang dài ra đều hai đầu** tới khi bốn góc sạch, tối đa 20 mm mỗi đầu (nới bằng đó thì mọi điểm đều ra khỏi vùng dấu → luôn có lời giải). Đường cắt dịch lên theo.
- **Không nới ngang:** vướng khổ cuộn 603 mm và job tối đa 576 mm của Cutting Master.
- Đo trên **nét vẽ thật**, không trên khung bao. Đo bằng **mm**, chỉ đổi sang pixel ở chỗ quét mặt nạ (đổi qua lại hai lần cộng dồn sai số làm tròn).
- Tìm hàng có mực **đầu tiên**: cẩn thận `break` trong vòng lặp lồng (từng chỉ thoát vòng trong → ra hàng cuối, nới 1 mm thay vì 15).
- Bản in PDF và TIF **không đổi kích thước**. Ví dụ đo thật: in 570 × 819 mm → cắt 570 × 851 mm.

**Log** khi dựng: kích thước trang, toạ độ 4 dấu (mm), số viền ngoài / viền lỗ / mảng bị bỏ — để thợ đối chiếu Job size và vị trí dấu trong Cutting Master.

> Thông số dấu chưa được xác nhận bằng file mẫu thật (đọc từ ảnh chụp màn hình Cutting Master) → **mọi thông số dấu nằm trong cấu hình**, không hard-code. Quy trình nghiệm thu: chồng bản in và bản cắt ra PNG để nhìn → so log với Cutting Master → cắt thử một tấm nhỏ trên phim thừa.

### 10.6 Bộ nhớ — yêu cầu ổn định (không phải tối ưu tốc độ)

Tấm 57 × 100 cm @300 DPI = 6.732 × 11.811 px. Ảnh ARGB cả tấm = 318 MB; mảng 5 kênh = 397 MB. Bản cũ giữ 4 mảng cả tấm cùng lúc → **hệ điều hành giết tiến trình** (không phải OutOfMemoryError nên không bắt được) → mất mọi job trong RAM → thợ nhận `JOB_NOT_FOUND` cho chính lần ghép vừa xong. Bản mới **bắt buộc**:

- **Render theo dải ngang** (mặc định 1.024 hàng, làm tròn lên bội số 64) thay vì cả tấm. Chỉ mặt nạ 1 byte/pixel là giữ cả tấm.
- Cắt dải bằng **tịnh tiến nguyên số pixel**, giữ nguyên CropBox (thu hẹp CropBox → lệch phần pixel, 0,32 % điểm W1 sai).
- Trước khi vẽ mỗi dải: **xoá nền về trong suốt** (`setBackground(0,0,0,0)` + `clearRect`) — nền mặc định của ảnh ARGB mới là **đen đặc**.
- Phép loang nền (10.3) nhìn cả tấm nên chạy **sau** khi mọi dải đã phân loại xong.
- Dữ liệu 5 kênh giữ **đã nén theo dải**, chỉ giải nén dải mà bộ ghi TIFF đang hỏi. Raster trả cho bộ ghi phải là **mảng riêng bắt đầu từ byte 0** (view có offset → mọi dải ghi ra nội dung dải đầu).
- Kết quả phải **giống từng byte** với cách render cả tấm cho hình vuông góc (nét cong lệch không đáng kể ở rìa do khử răng cưa).
- **Chặn trước khi dựng:** ước lượng bộ nhớ; vượt `heap tối đa × 0,5` hoặc vượt 100 megapixel → `TIFF_TOO_LARGE` kèm **con số DPI nên đặt** hoặc gợi ý giảm chiều dài tối đa mỗi file.
- **JVM:** G1 GC + periodic GC (60 s) + `MinHeapFreeRatio=10 / MaxHeapFreeRatio=30` để trả RAM về hệ điều hành lúc rảnh; `MaxRAMPercentage=60` (chừa chỗ cho bộ nhớ ngoài heap của ImageIO / ICC / PDFBox).

---

## 11. Vòng đời job, lưu trữ, dọn rác

- Job là bản ghi **bất biến**; đổi trạng thái là sinh bản mới: `PENDING(0)` → `RUNNING(10)` → `DONE(100, result)` / `FAILED(error)`. Lưu kèm tham số gốc (không trả ra API), `createdAt`, `finishedAt`.
- Chạy trên **thread pool riêng** (core = max(2, nhân/2), max = max(4, nhân), hàng đợi 100). Với Spring: runner `@Async` phải là **bean riêng** (tự gọi trong cùng bean thì chạy đồng bộ, treo request).
- Mọi ngoại lệ trong job được ghi vào job (`FAILED`), không ném ra ngoài. Log khi xong: thời gian, số tấm, chiều dài, lấp đầy.
- **Dọn rác:** mỗi 60 phút xoá job, file, ảnh xem trước, bản đồ chiếm chỗ cũ hơn **48 giờ** (`0` = tắt). Dọn đủ cả ba chỗ: **đĩa**, **map metadata**, **cache preview** (từng quên một map → RAM không bao giờ về mức nghỉ). Có hàm dọn thì phải có lịch gọi nó và test chứng minh.
- Thư mục lưu trữ nên trỏ vào volume bền để file sống qua lần deploy (job vẫn mất khi restart nếu còn để RAM).

---

## 12. Những gì nhánh `fix/ram-va-chieu-dai-mau` đã sửa (bản mới phải giữ)

6 commit trước `main`, theo thứ tự thời gian:

| Commit | Lỗi thợ gặp | Cách sửa (yêu cầu cho bản mới) |
|---|---|---|
| `e0c30f0` | Máy chủ hết RAM khi dựng TIF → 502 → khởi động lại → `JOB_NOT_FOUND`. RAM nằm phẳng 80 % suốt đêm. | Cờ JVM ở 10.6; trần TIF đo theo **heap thật** (không chỉ số pixel cố định), báo kèm DPI nên đặt; trần cache export theo heap. |
| `e0c30f0` | Mẫu 8 × 0,9 cm, 11 bản ghi **0 cm** trong bảng chia tiền. | Chia chiều dài theo **diện tích riêng** của bản (mục 9). Có test `ThinPieceLengthTest`. |
| `694c200` | Dựng TIF/file cắt giữ nhiều mảng cả tấm → RAM 875 MB / tấm. | **Render theo dải** (10.6) → ~328 MB, không còn phụ thuộc chiều dài tấm; file ra **giống từng byte** bản cũ. |
| `2cdbe60` | Tải zip 11 tấm: đợi ~5 phút rồi `INTERNAL_ERROR` (OOM khi gom zip trong bộ nhớ); sau đó zip bị **cắt cụt** ở 30 s mà vẫn tải về được. | Zip **ghi thẳng ra luồng**; timeout async 30 phút; test zip phải có **đủ số tấm**. Gộp hai lượt render TIF thành một. |
| `74667b0` | Zip 11 tấm: file thứ 9 rỗng 0 KB, 2 file cuối mất — cache giữ mọi tấm vừa dựng làm tràn heap. | Đường zip **không ghi cache** (chỉ đọc); trần cache hạ xuống heap/8; tấm hỏng → `LOI-tamNN.txt` trong zip thay vì làm đứt zip. |
| `2710523` | Chữ **trắng** trong file PDF không có lót trắng (và không có mực nào) trong TIF. | Phép loang nền chỉ áp trong **vùng các bản đến từ ảnh**. |
| `e224530` | Như trên nhưng với **PNG đã tách nền** có chi tiết trắng. | Tiêu chí đúng là **có kênh trong suốt hay không**: PDF và PNG có alpha → giữ trắng; chỉ JPG / PNG không alpha mới loang nền. Không xác định được → coi như có alpha (thà thừa lót trắng còn hơn thiếu chữ). |

Kết quả nhánh: **133/133 test pass**, nghiệm thu thuật toán không đổi (478,2 cm / 92,45 % / 71,9 % / 1 tấm), frontend type-check + 18 test sạch.

---

## 13. Cấu hình (biến môi trường)

Mọi giá trị đọc từ biến môi trường, có mặc định chạy được ngay.

**Frontend** (biến build-time của Vite — đổi phải build lại):

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `VITE_API_BASE_URL` | trống | URL gốc backend, không có `/` cuối |
| `VITE_USE_MOCK` | tự suy ra | `true` / `false`; trống = demo nếu chưa có URL backend |

**Backend:**

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `PORT` | 8080 | Cổng |
| `APP_STORAGE_PATH` | `./storage` | Thư mục lưu file |
| `APP_MAX_FILE_SIZE` | 52428800 | Byte tối đa mỗi file (đồng bộ với giới hạn multipart 50 MB / request 200 MB) |
| `APP_CORS_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Origin được phép, cách nhau dấu phẩy |
| `APP_TRIM_ENABLED` | true | Cắt trắng PDF |
| `APP_RETENTION_HOURS` | 48 | Giữ dữ liệu bao lâu; 0 = không dọn |
| `APP_RETENTION_SWEEP_MINUTES` | 60 | Chu kỳ dọn |
| `APP_EXPORT_TIMEOUT_MS` | 1800000 | Thời hạn phản hồi dạng luồng (zip) |
| `APP_TIFF_DPI` | 300 | DPI bản TIF |
| `APP_TIFF_MAX_MEGAPIXELS` | 100 | Trần tuyệt đối số pixel một tấm |
| `APP_TIFF_MAX_HEAP_FRACTION` | 0.5 | Phần heap tối đa cho một tấm |
| `APP_TIFF_BAND_ROWS` | 1024 | Số hàng mỗi dải render |
| `APP_TIFF_COMPRESSION` | Deflate | hoặc LZW |
| `APP_TIFF_COMPRESSION_QUALITY` | 0.3 | Mức nén 0..1 |
| `APP_TIFF_COLOR_MODE` | cmyk | hoặc rgb |
| `APP_TIFF_CMYK_PROFILE` | `classpath:color/USWebCoatedSWOP.icc` | Đường dẫn hồ sơ ICC |
| `APP_TIFF_TRANSPARENT_LAYER` | true | Kèm lớp trong suốt |
| `APP_TIFF_LAYER_ZIP` | true | Nén lớp ZIP (false = RLE) |
| `APP_TIFF_WHITE_ENABLED` | true | Kèm kênh W1 |
| `APP_TIFF_WHITE_CHANNEL` | W1 | Tên kênh, phải khớp RIP |
| `APP_TIFF_WHITE_ALPHA_THRESHOLD` | 128 | Ngưỡng alpha "có hình" |
| `APP_TIFF_WHITE_CHOKE` | 1 | Số pixel co lớp trắng |
| `APP_TIFF_WHITE_TOLERANCE` | 6 | Dung sai "gần trắng" khi loang nền |
| `APP_CUT_DPI` | 300 | DPI dò viền cắt |
| `APP_CUT_OFFSET_MM` | 0.0 | Nở đường cắt ra ngoài |
| `APP_CUT_SIMPLIFY_MM` | 0.1 | Sai số rút gọn đỉnh |
| `APP_CUT_MIN_AREA_MM2` | 0.2 | Lọc mảng nhỏ |
| `APP_CUT_STROKE_PT` | 0.25 | Độ dày nét cắt |
| `APP_CUT_SPOT_NAME` | CutContour | Tên màu mực riêng |
| `APP_CUT_MARK_LENGTH_MM` | 15.0 | Cạnh dấu |
| `APP_CUT_MARK_THICKNESS_MM` | 1.0 | Độ dày dấu |
| `APP_CUT_MARK_TOP_LEFT` / `_TOP_RIGHT` / `_BOTTOM_LEFT` / `_BOTTOM_RIGHT` | SQUARE_WITH_EDGE | Kiểu dấu từng góc |
| `APP_CUT_MARK_CLEARANCE_MM` | 5.0 | Vùng trống quanh dấu |

---

## 14. Triển khai: frontend trên Render, backend trên máy chủ riêng

### 14.1 Frontend — Render Static Site

- Build: `npm ci && npm run build`, thư mục xuất `dist`, Node 20.
- Biến môi trường (đặt trên Render, **trước** khi build): `VITE_API_BASE_URL=https://<domain-backend>` và `VITE_USE_MOCK=false`. Đổi biến phải build lại.
- **Rewrite** mọi đường dẫn `/*` → `/index.html` (status 200) cho SPA history mode, nếu không tải lại trang `/nest/result/<id>` sẽ 404.
- Không khai biến runtime trong file cấu hình repo nếu nó đè biến đặt trên dashboard (bẫy cũ với `netlify.toml`).
- Link tải file trỏ **thẳng tới domain backend** (thẻ `<a href>` mở tab mới), nên tải file không đi qua Render.

### 14.2 Backend — máy chủ riêng

- Chạy bằng Docker (Dockerfile hai giai đoạn sẵn có: build Maven JDK 21 → chạy JRE 21, người dùng thường, `JAVA_OPTS` như 10.6) hoặc chạy jar trực tiếp với cùng cờ JVM.
- **Volume** cho `APP_STORAGE_PATH` để file sống qua lần cập nhật.
- **HTTPS bắt buộc** (frontend trên Render chạy HTTPS; gọi HTTP sẽ bị trình duyệt chặn mixed content). Đặt reverse proxy (Caddy / Nginx) trước backend.
- Reverse proxy phải:
  - cho body upload tối thiểu **200 MB** (Nginx: `client_max_body_size 200m`);
  - **timeout đọc/gửi dài** (≥ 30 phút) cho endpoint zip, và **tắt buffering phản hồi** cho nó (Nginx: `proxy_buffering off`) để zip chảy ra từng tấm;
  - không nén lại file TIF/zip (đã nén sẵn).
- `APP_CORS_ORIGINS` = đúng domain Render của frontend (và domain tuỳ chỉnh nếu có), không để `*`.
- RAM: cấp heap theo DPI và khổ/dài tấm lớn nhất xưởng dùng; trần `max-heap-fraction` sẽ báo lỗi rõ ràng thay vì để tiến trình bị giết.
- Chỉ chạy **một** instance (job, file metadata đang ở RAM của tiến trình). Muốn nhiều instance phải chuyển job/metadata sang DB và storage dùng chung trước.
- Healthcheck: endpoint đơn giản trả 200 (bản cũ chưa có — nên thêm).

---

## 15. Kiểm thử và nghiệm thu

Bản cũ có 133 test backend + 18 test frontend. Bản mới tối thiểu phải có:

**Thuật toán**
- Mọi test chạy kèm **bộ kiểm bất biến**: không chồng lấn; trong khổ trừ lề; mọi cặp cách ≥ gap; đủ số lượng; không vượt `maxSheetLength`; giữ hướng khi khoá xoay; chạy hai lần ra cùng kết quả.
- Bộ nghiệm thu 8.6 với ngưỡng sát số đo (hiện ≥ 92 % lấp đầy), in log `[NGHIEM THU] chieu dai = … | lap day = … | tiet kiem = … | so tam = …`. Số không được xấu đi.
- **Bài quét ngẫu nhiên hạt cố định** (≥ 22 cấu hình × các chế độ, nhiều lề/gap) cho giới hạn chiều dài — thứ duy nhất từng bắt được lỗi vượt giới hạn.
- FREE và TRUE_SHAPE: hình chữ L (`samples/thu-chu-L-108.pdf`) phải cho bản nhỏ chui vào; nét vẽ không chạm nhau.
- `fillRate` không bao giờ > 1; `byType` cộng lại bằng tổng; mẫu mảnh chui trong khung mẫu khác không ra 0.

**Đọc file**
- Mọi file `samples/*` ra đúng kích thước theo tên. PNG 300 DPI → 50,8 × 33,87 mm. `loi-qua-kho-60x59.pdf` → `ITEM_WIDER_THAN_SHEET`. `thu-hinh-nho-giua-A4.pdf` → `trimmed = true`. Hình tô màu trắng vẫn được tính. Trang trắng → lùi về hộp trang.
- Ảnh xem trước ≤ 360 px và khớp tỷ lệ content box.

**Xuất file**
- PDF: khổ trang đúng tới **0,1 mm**; đọc ngược vùng có nét vẽ của từng bản trong PDF thành phẩm khớp ô thuật toán đã định (bắt lỗi bù toạ độ khi cắt trắng).
- TIF: **đọc ngược tag** (DPI, 5 kênh, ExtraSamples = 0, ICC, khối 34377 / 37724); **đếm toàn bộ** pixel W1 theo nhóm (đừng chấm vài điểm); W1 đúng chiều giá trị; chữ trắng trong PDF và chi tiết trắng trong PNG alpha **có** lót; nền trắng của JPG **không** có lót; render theo dải ra giống render cả tấm.
- Trần bộ nhớ: heap nhỏ → `TIFF_TOO_LARGE` có gợi ý DPI, không chết tiến trình.
- File cắt: chồng lên bản in ra PNG để nhìn; chữ nhỏ còn nguyên; có viền lỗ; bề ngang bằng bản in; ca **hình không chạm mép** và ca **hình chạm góc** để kiểm phép nới.
- Zip: có **đủ** số tấm, giải nén đọc hết không lỗi; ca một tấm hỏng → có `LOI-tamNN.txt`.

**Giao diện**
- Parse ô số (`2,5`, `2.5`, rỗng, chữ); khoá tỷ lệ không trôi sau nhiều lần sửa; canh gác route bước 2; tải lại trang kết quả vẫn hiện; job FAILED hiện đúng message.

**Trong Photoshop / Cutting Master** — xem checklist 10.4 và 10.5.

---

## 16. Chỗ tài liệu cũ lệch code

- `README.md` mục 9 nói "chỉ xếp hình chữ nhật" — sai, đã có `FREE` và `TRUE_SHAPE`.
- Javadoc đầu `CutContours.java` nói "chỉ lấy viền ngoài" — `CutComposer` lấy **cả viền lỗ**. Theo code.
- `docs/API.md` không liệt kê `angleDeg`, `sourceWMm`, `sourceHMm` trong placement — backend có trả.
- Kiểu `Placement` ở frontend **thiếu `angleDeg`**; preview chỉ vẽ khung chữ nhật thẳng và tooltip chỉ biết "xoay 90 độ" → ở `TRUE_SHAPE` preview không thể hiện góc chéo.
- `drawCutLines`: backend mặc định `true` khi thiếu trường; giao diện mặc định **tắt** và luôn gửi.
- Câu chữ giao diện còn nói "tải PDF gửi máy in" trong khi định dạng chính là TIF.
- `samples/README.md` còn tên file tiền tố cũ `printnest-`; đúng là `minh-tri-`.
- `docs/DEPLOYMENT.md` viết cho Netlify + Render backend — không còn đúng với hướng triển khai mới (mục 14).

---

## 17. Gợi ý làm chỉn chu hơn (không bắt buộc, không đụng bất biến)

- **Preview bố trí** vẽ ảnh xem trước thật của từng mẫu (đúng góc xoay, kể cả góc chéo) thay vì khối màu; có zoom / kéo.
- **Ước lượng trước** ở bước 2: số mét lý thuyết tối thiểu = diện tích ÷ bề rộng khả dụng, để thợ biết cỡ đơn trước khi chạy.
- **Tiến độ thật** cho job (hiện nhảy 0 → 10 → 100) và cho việc dựng file tải về (hiện chỉ có tab trình duyệt quay).
- Hiển thị cảnh báo khi một mẫu **không đủ điều kiện xếp lồng** (ảnh, đã sửa kích thước, trang có `/Rotate`) mà người dùng đang chọn FREE / TRUE_SHAPE — hiện app âm thầm coi nó là khối đặc.
- Lưu đơn đang soạn vào `localStorage` để lỡ tải lại trang ở bước 1–2 không mất.
- Endpoint healthcheck và một trang "trạng thái máy chủ" nhỏ (đang online, RAM còn) cho thợ biết khi nào backend vừa khởi động lại.
- Bốn ô "Sắp có" ở trang chủ ứng với các hướng mở rộng: Thư viện file (bỏ xoá file + endpoint liệt kê), Lịch sử ghép (kho job dùng DB), Preset khổ in, Báo cáo tiết kiệm.


Nhưng thay vì Deploy backend trên render như ban đầu thì deploy trên server của tôi (đang bật - termius), còn thiếu gì thì bạn tự tải app. CÓ thể test file tif và pdf trên adobe (đã có).
Và hãy thao tác trên visual studio code. Ban đầu chưa bắt đầu code hoặc chuẩn bị cái gì thì có thể chia việc cho agent làm còn khi bắt đầu thì như đã đề cập với plugin hãy thử sử dụng nó để tôi xem hiệu quả.

Hãy remote máy tôi luôn