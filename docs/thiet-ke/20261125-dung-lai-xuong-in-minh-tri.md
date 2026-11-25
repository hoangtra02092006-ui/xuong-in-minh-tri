# Thiết kế: dựng lại web Xưởng in Minh Trí

Trạng thái: **Đã duyệt 25/11** (người quản lý: "ok vào làm đi"), gồm phương án dựng sẵn TIF ở mục 4a.
Đầu vào: [tóm tắt yêu cầu](../yeu-cau/20261125-dung-lai-xuong-in-minh-tri.md), [đặc tả](../yeu-cau/dac-ta-goc.md), code cũ repo `Push-clean-leather-file` nhánh `fix/ram-va-chieu-dai-mau`.

## 0. Quyết định lớn nhất: dựng lại bằng cách nào

Code cũ có khoảng 10.000 dòng Java backend (23 file test, 133 test) và 5.400 dòng frontend Vue. Phần khó nhất (thuật toán xếp đạt 478,2 cm, TIF khớp từng byte với file mẫu Photoshop, file cắt, chống hết RAM) đã được sửa qua nhiều lần lỗi thật.

| | A. Chuyển có chọn lọc (đề xuất) | B. Viết lại toàn bộ từ đặc tả |
|---|---|---|
| Backend | Lấy engine xếp, xuất PDF/TIF/cắt cùng test của chúng sang repo mới; sắp lại module, dọn code, viết lại phần lộn xộn; thêm test còn thiếu | Viết mới mọi lớp theo đặc tả |
| Frontend | Viết mới (giao diện được đổi tự do), giữ hợp đồng API | Viết mới |
| Thời gian ước lượng | 4–6 ngày làm việc của agent | 3–4 tuần |
| Rủi ro | Mang theo vài chỗ thiết kế cũ chưa đẹp (sửa dần, có test giữ) | Cao: dễ mất các sửa lỗi tinh vi (byte order khối Photoshop, chiều W1, loang nền, chia tiền giấy), khó đạt lại số nghiệm thu |
| Đúng ý "làm chỉn chu hơn" | Có, ở cấu trúc, giao diện, test, triển khai | Có, nhưng tốn gấp 4–5 lần cho cùng kết quả |

Agent đề xuất A. **Người quản lý chọn B (25/11).** Cách giảm rủi ro của B:
- **App cũ làm chuẩn đối chiếu:** chạy backend cũ trên bộ `samples/` để sinh file kết quả chuẩn (JSON bố trí, PDF, TIF, file cắt). Bản mới phải ra số nghiệm thu bằng hoặc tốt hơn, và TIF/file cắt khớp chuẩn (so từng byte với ca hình vuông góc; ca nét cong so theo số pixel lệch).
- **Danh sách bẫy đã gặp** (đặc tả mục 10, 12) chép thành test viết **trước** code của phần đó.
- Đọc code cũ chỉ để hiểu khi bị kẹt, không chép nguyên khối.

## 1. Cấu trúc dự án tổng quan

**Có backend**, vì: logic tính toán thật (xếp, xuất TIF 5 kênh) không chạy nổi trên trình duyệt với file lớn; nhiều thợ dùng chung; dự kiến mở rộng (thư viện file, lịch sử ghép cần database).

```
Thợ, chủ xưởng (trình duyệt, mạng nội bộ / Internet)
   │ HTTPS
   ▼
Frontend tĩnh (Render Static Site) ──REST JSON, mm, CORS──► nginx có sẵn (HTTPS certbot, in.thanhtungtextile.vn) ─► Backend Spring Boot (Docker)
                                                              trên máy chủ Tino 180.93.136.187
   link tải PDF/TIF/CẮT/zip trỏ thẳng tới backend            │ dùng chung máy với n8n
                                                              ▼
                                                    volume storage/ (file gốc)
```

**Bản đồ module backend** (phụ thuộc một chiều, trên phụ thuộc dưới):

| Module | Lo việc gì | Phụ thuộc |
|---|---|---|
| `api` | Controller, thân lỗi chung, CORS, healthcheck | file, nesting, export |
| `export` | Dựng PDF, TIF (CMYK, W1, lớp trong suốt), file cắt, zip, cache | nesting (kết quả), file, raster |
| `raster` (mới tách) | Render theo dải, mặt nạ "có hình", loang nền, co/nở mặt nạ: **một chỗ** dùng chung cho TIF và file cắt | file |
| `nesting` | Tạo job, chạy nền, số liệu (fillRate, byType) | engine, job, file |
| `engine` | Thuật toán xếp thuần, số nguyên 1/100 mm, không phụ thuộc Spring | không |
| `file` | Upload, đọc kích thước, content box, bản đồ chiếm chỗ, preview | storage |
| `job`, `storage` | Interface kho job / kho file + bản trong RAM / trên đĩa, dọn rác | không |

**Cây thư mục chính**
```
xuong-in-minh-tri/
  backend/            Spring Boot, Maven wrapper, Dockerfile
  frontend/           Vue 3 + Vite
  samples/            file mẫu + IN6 2209.tif + generate.mjs
  deploy/             docker-compose.yml, cấu hình nginx, render.yaml, hướng dẫn
  docs/               yeu-cau/, thiet-ke/, adr/, API.md, van-hanh.md
  CLAUDE.md           lệnh, bản đồ thư mục, luật riêng dự án
```

## 2. Công nghệ đề xuất (người quản lý đã đồng ý giữ stack cũ 25/11)

| Phần | Chọn | Lý do gắn với dự án |
|---|---|---|
| Backend | Java 21, Spring Boot 3.3, Maven wrapper | Có đủ thư viện cho TIFF 5 kênh tag tuỳ biến, ICC, ImageIO; code cũ chạy được ngay. Java 21 thay 17 vì Docker cũ đã dùng JDK 21. |
| PDF | Apache PDFBox 3 | Nhúng form XObject, Separation, OCG, render có alpha |
| Frontend | Vue 3 + TypeScript + Vite + Pinia + Vue Router, Vitest | Người quản lý quen Vue; thêm Vitest để test thật thay script `.mjs` cũ |
| Giao diện | CSS token tự viết (như cũ), không thêm thư viện UI | App 4 màn, thư viện UI chỉ tăng dung lượng |
| Chạy backend | Docker (image có sẵn JRE 21) sau nginx đang có trên máy chủ Tino | Máy chủ đã chạy nginx + certbot cho n8n: thêm một server block riêng cho in.thanhtungtextile.vn, không đụng site n8n; không cài thêm proxy thứ hai |

Máy người quản lý đang có Java 20: cần cài JDK 21 (agent tự cài) để build ngoài Docker.

## 3. Kiến trúc: monolith chia module, lợi và hại

| | Lợi | Hại |
|---|---|---|
| Trước mắt | Một tiến trình, một Docker image, deploy một lệnh; job trong RAM không cần hàng đợi ngoài | Dựng TIF nặng chạy cùng tiến trình với API: một tấm lớn chiếm RAM thì upload/poll chậm theo |
| Khi lớn lên (thêm thư viện file, lịch sử, báo cáo) | Thêm module mới cạnh module cũ; engine thuần Java test độc lập nên build test nhanh | Build backend ~1–2 phút; nếu thêm nhiều chức năng không liên quan xuất file thì nên tách |
| Người mới | Bản đồ module + một chiều phụ thuộc, đọc `engine` không cần hiểu Spring | Phần `export` khó (định dạng Photoshop), cần tài liệu riêng |
| Chi phí | Dùng lại máy chủ Tino đang trả, không thêm tiền | Dùng chung 6 GB RAM với n8n |

Ước lượng cỡ: 1 người quản lý + agent, 4 màn hình, ~6 module. Monolith là vừa.
**Điều kiện đổi kiến trúc:** khi cần nhiều instance hoặc dựng TIF làm chậm thợ khác thì tách "worker xuất file" thành tiến trình riêng, job chuyển sang database + hàng đợi.

## 4. Tổng quan giao diện

Giữ đúng hành vi và câu chữ đặc tả mục 5, làm lại hình thức (skill giao diện làm sau, có ảnh chụp để duyệt).

| Màn | Bố cục chính | Thông tin |
|---|---|---|
| Trang chủ "Công cụ sản xuất" | Lưới 5 ô chức năng, 1 ô hoạt động | Ghép file in; 4 ô "Sắp có" khoá |
| Bước 1 "Nạp file in" | Stepper · vùng kéo thả · bảng hình · chân dính có tổng + "Tiếp tục →" | Ảnh nhỏ, tên, rộng/dài cm (khoá tỷ lệ), số lượng, cho xoay, hoàn tác/xoá |
| Bước 2 "Tham số in" | Form trái (Khổ in, Khoảng cách & xoay) · tóm tắt phải · **ước lượng số mét tối thiểu** (mới) | Khổ, lề, dài tối đa, gap, cách xếp, xoay, đường cắt; **cảnh báo mẫu không xếp lồng được** (mới) |
| Bước 3 "Kết quả ghép" | 4 ô số · bảng "Từng mẫu ăn mấy mét" · bảng từng file + tải TIF/PDF/CẮT · xem trước bố trí theo tab tấm · hàng nút tải tất cả | Preview **vẽ đúng góc xoay chéo** (mới) |
| 404 | Thông báo + về trang chủ | |

### 4a. Dựng sẵn TIF và tiến độ (người quản lý duyệt 25/11)
- Ghép xong, máy chủ tự dựng TIF ở nền, **lần lượt từng tấm** (không dựng song song để giữ trần RAM 1,5 GB), từ tấm 1. File lưu **ra ổ đĩa** (không giữ trong RAM), tự xoá theo hạn dọn rác.
- Màn kết quả hiện bố trí ngay. Dòng tiến độ chung "Đang chuẩn bị file TIF: 2/5 tấm sẵn sàng" + thanh tiến độ; xong hết thì đổi "Tất cả file TIF đã sẵn sàng" rồi ẩn.
- Cột TIF từng tấm: **Sẵn sàng** (nút TIF kèm dung lượng, vd "TIF 5,2 MB") · **Đang dựng…** · **Đang chờ** · **Lỗi · Thử lại** (rê chuột thấy lý do). Nút **không khoá**: bấm tấm đang chờ thì tấm đó được dựng trước và tự tải khi xong.
- "Tải tất cả TIF" không khoá, nhãn kèm tiến độ; bấm sớm thì zip lấy tấm đã có, tấm chưa có dựng nối tiếp.
- PDF và file CẮT dựng khi bấm (nhanh), link hiện vòng quay khi đang dựng.
- Đóng tab hay tải lại trang thì máy chủ vẫn dựng tiếp; mở lại thấy đúng trạng thái.
- Số đo app cũ (tấm 57 × 100 cm, 300 DPI): dựng ~4 giây, file 5–7 MB, lớp trong suốt thêm ~55% dung lượng (giữ lại vì thợ cần ô caro).
- API thêm: trạng thái xuất của từng tấm nằm trong `GET /nesting/jobs/{id}` (trường mới `result.sheets[].tif: {status, sizeBytes, error}`), và `POST /nesting/jobs/{id}/sheets/{i}/tif/prioritize` để đưa tấm lên trước. Chỉ thêm trường, không phá hợp đồng cũ.

Luồng: Trang chủ → Bước 1 → Bước 2 → (Ghép) → Bước 3 → quay lại Bước 2 để ghép lại. Đơn đang soạn lưu trong trình duyệt (mới), tải lại trang ở bước 1–2 không mất.

## 5. Dữ liệu chính

Chưa có database (giữ như cũ, kho là interface).
- `StoredFile`: id, tên gốc, loại PDF/ẢNH, kích thước dùng xếp và kích thước gốc (mm), contentBox (pt), pageRotation, có kênh trong suốt, bản đồ chiếm chỗ, createdAt.
- `Job` (bất biến): id, trạng thái, progress, tham số gốc, kết quả (`sheets[]`, `stats`), lỗi, createdAt, finishedAt.
- `Sheet` → nhiều `Placement` (fileId, categoryIndex, x/y/w/h mm, rotated, angleDeg, sourceW/H).
- Quan hệ: Job → nhiều Sheet → nhiều Placement → một StoredFile.

## 6. Hợp đồng API

Giữ **nguyên** đặc tả mục 6 (`/api/v1`, mm, thân lỗi `{error:{code,message,details}}`, 11 mã lỗi). Thêm:
- `GET /api/v1/health` → `200 {"status":"UP","startedAt":"…","heapUsedMb":…,"heapMaxMb":…}` cho trang trạng thái nhỏ.
- `progress` của job đi theo tiến độ thật (số phương án đã thử / tổng) thay vì 0 → 10 → 100. Không đổi kiểu dữ liệu.
Tài liệu `docs/API.md` cập nhật thêm `angleDeg`, `sourceWMm`, `sourceHMm`.

## 7. Hạn chế của thiết kế này

- **RAM dùng chung (đo 25/11):** máy 5,9 GB, còn khoảng 3,4 GB, swap đã dùng 1,2 GB; chung máy có n8n (queue + worker + postgres + redis + nocodb), syncbase, các bot Python. Đề xuất giới hạn container backend **1,5 GB**, heap ~1 GB, `APP_TIFF_MAX_HEAP_FRACTION` giữ 0,5. Render theo dải ~330 MB/tấm 57×100 cm nên vẫn chạy được; tấm dài hơn sẽ bị từ chối rõ ràng.
- **Ổ đĩa còn 12 GB (80%):** giữ dọn rác 48 giờ, giới hạn 50 MB/file; thêm cảnh báo trong log khi ổ còn < 3 GB. Tấm dài ở 300 DPI có thể bị từ chối `TIFF_TOO_LARGE` sớm hơn; khi đó thợ giảm chiều dài tối đa mỗi file (mặc định 100 cm vẫn an toàn: ~330 MB/tấm).
- Job và metadata vẫn trong RAM: khởi động lại backend thì mất lần ghép đang dở (thông báo lỗi nói rõ).
- Một instance duy nhất.
- Dấu định vị file cắt chưa có file mẫu thật; mọi thông số để trong cấu hình, nghiệm thu bằng cắt thử.
- Hướng khác: tách worker xuất file (mục 3), hoặc thêm PostgreSQL khi làm "Lịch sử ghép".

## 8. Ngoài phạm vi

- Tối ưu thuật toán dựng TIF / file cắt cho nhanh hơn (đặc tả nói chủ dự án tự làm sau). Riêng **thời gian chờ** của thợ đã đưa vào phạm vi qua dựng sẵn ở nền (mục 4a).
- 4 ô "Sắp có" (thư viện file, lịch sử, preset, báo cáo): cần database, để giai đoạn sau.
- Đăng nhập: đặc tả nói không cần. **Lưu ý:** backend ra Internet thì ai biết địa chỉ cũng gọi được. Đề xuất giai đoạn này chỉ giới hạn CORS + giới hạn dung lượng; nếu người quản lý muốn chặn thêm thì thêm mật khẩu chung một lớp ở nginx (ít việc).

## 9. Ranh giới cho agent

- **Luôn làm:** chạy test liên quan trước khi commit; chạy bộ nghiệm thu thuật toán khi đụng `engine`; giữ 12 bất biến, mỗi bất biến có test; làm trên nhánh + PR.
- **Hỏi người quản lý trước:** thêm thư viện; đổi hợp đồng API; đổi tham số JVM / heap trên máy chủ; bất kỳ thao tác nào trên máy chủ có thể ảnh hưởng n8n (Docker, nginx, tường lửa, cổng 80/443); deploy production.
- **Không bao giờ:** đưa mật khẩu, khoá SSH vào repo; xoá hoặc bỏ qua test đang lỗi; dừng/xoá container n8n; push thẳng `main`.

## 10. Kế hoạch test

| Tiêu chí (đặc tả) | Test |
|---|---|
| Bất biến 1–5, 8 (xếp) | Bộ kiểm bất biến chạy kèm mọi test engine; quét ngẫu nhiên hạt cố định ≥ 22 cấu hình × 3 chế độ |
| Nghiệm thu 8.6 | `AcceptanceTest`: ≥ 478,2 cm / 92,45 % / 71,9 %; 120 cm → 5 tấm ≤ 494,1 cm; in log `[NGHIEM THU]` |
| FREE, TRUE_SHAPE | Hình chữ L cho bản nhỏ chui vào, nét không chạm |
| Bất biến 9, 11 (content box, chia mét) | Test đọc `samples/*` đúng kích thước; `byType` cộng đúng tổng; mẫu mảnh không ra 0 |
| Bất biến 6, 10 (PDF vector, 3 định dạng cùng bố trí) | Đọc ngược PDF thành phẩm, vùng nét khớp ô ±0,1 mm |
| TIF 10.2–10.4 | Đọc ngược tag; đếm toàn bộ pixel W1; chữ trắng PDF có lót, nền JPG không lót; dải = cả tấm từng byte |
| File cắt 10.5 | Chữ nhỏ còn; có viền lỗ; bề ngang = bản in; ca chạm góc nới dài |
| RAM 10.6 | Heap nhỏ → `TIFF_TOO_LARGE` có DPI gợi ý; zip đủ tấm, tấm hỏng có `LOI-tamNN.txt` |
| API, dọn rác | Test tích hợp controller; dọn đủ đĩa + map + cache |
| Giao diện | Vitest: parse số `2,5`/`2.5`, khoá tỷ lệ không trôi, canh gác route, tải lại trang kết quả, job FAILED; mock chạy bộ nghiệm thu |
| Kiểm tay (sau khi unit test xanh) | Mở TIF trong Photoshop theo checklist 10.4; mở file cắt trong Illustrator theo 10.5; ảnh chụp giao diện desktop + mobile |

Độ phủ > 80% cho `engine`, `nesting`, `file`, `export`, `raster`, `api`.

## 11. Ước lượng và chia việc cho agent

Tổng khoảng 3–4 tuần làm việc của agent (phương án B), chưa tính thời gian chờ duyệt. Phần tốn nhất: xuất TIF (CMYK + W1 + lớp Photoshop) và file cắt, khoảng 40%.

| Đợt | Việc | Agent | Song song |
|---|---|---|---|
| 1 | Khởi tạo repo: khung, CLAUDE.md, CI, chép samples + ICC; sinh bộ file chuẩn từ app cũ | 1 | Không |
| 2a | `engine` + `nesting`: MaxRects, Shelf, tìm nhị phân, leo đồi, chia tấm, FREE, TRUE_SHAPE, số liệu; bộ bất biến + nghiệm thu | 1 | Song song 2b, 2c |
| 2b | `file` + `raster`: đọc PDF content box, ảnh DPI, bản đồ chiếm chỗ, preview, render theo dải, mặt nạ | 1 | Song song |
| 2c | Frontend 4 màn + mock + Vitest theo hợp đồng API | 1 | Song song (dùng mock) |
| 3a | `export` PDF + file cắt | 1 | Song song 3b, sau 2a, 2b |
| 3b | `export` TIF (ICC, W1, khối Photoshop 34377/37724) + zip + cache + chặn RAM | 1 | Song song |
| 4 | Ghép, healthcheck, tiến độ thật, đối chiếu toàn bộ với bộ file chuẩn | 1 | Không |
| 5 | Review độc lập | 1 | Không |
| 6 | Gói phát hành: Docker, nginx, Render; người quản lý bấm deploy | 1 + người quản lý | Không |

Code làm trên máy người quản lý qua remote (VS Code) theo yêu cầu; agent song song ở đợt 2 chạy mỗi agent một nhánh riêng.

## ADR đề xuất
- ADR-001 Chiến lược dựng lại: viết lại toàn bộ, app cũ làm chuẩn đối chiếu (mục 0, người quản lý đã chọn).
- ADR-002 Kiến trúc monolith chia module (mục 3).
- ADR-003 Hosting: frontend Render, backend Docker sau nginx có sẵn trên máy chủ Tino dùng chung n8n (mục 1, 7).
- ADR-004 File bản quyền (hồ sơ màu Adobe, file in thật) không nằm trong repo công khai.
Đã viết trong `docs/adr/`.
