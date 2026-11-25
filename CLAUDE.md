# Xưởng in Minh Trí

> Dùng plugin `bo-chuan` (bật sẵn trong `.claude/settings.json`). File này chỉ ghi phần riêng của dự án.

## Tổng quan
- Web xếp bản in DTF/decal lên cuộn khổ cố định cho tốn ít mét nhất, xuất PDF vector, TIF (CMYK + mực trắng W1 + lớp trong suốt) cho RIP, và file CẮT cho máy cắt Graphtec. Người dùng: thợ và chủ xưởng, không đăng nhập, giao diện tiếng Việt.
- Trạng thái: đang dựng lại từ đầu (ADR-001), chưa có người dùng trên bản mới.
- Yêu cầu: `docs/yeu-cau/20261125-dung-lai-xuong-in-minh-tri.md` (đặc tả đầy đủ: `docs/yeu-cau/dac-ta-goc.md`).
- Thiết kế: `docs/thiet-ke/20261125-dung-lai-xuong-in-minh-tri.md`. Luật: `CONSTRAINTS.md`.

## Công nghệ
- Backend: Java 21, Spring Boot 3.3, Apache PDFBox 3, Maven wrapper. Frontend: Vue 3 + TypeScript + Vite, Pinia, Vue Router, Axios, Vitest.
- Chưa có database (job và metadata trong RAM, file trên đĩa).
- Hosting: frontend Render Static Site; backend Docker sau nginx trên máy chủ Tino, tên miền `in.thanhtungtextile.vn` (ADR-003).

## Lệnh (đã chạy thử 25/11 trên Linux; PowerShell dùng `.\mvnw.cmd` thay `./mvnw`)
| Việc | Lệnh |
|---|---|
| Cài frontend | `cd frontend; npm ci` |
| Chạy backend local | `cd backend; ./mvnw spring-boot:run` → http://localhost:8080/api/v1/health |
| Chạy frontend local | `cd frontend; npm run dev` → http://localhost:5173 (không khai `VITE_API_BASE_URL` = chế độ demo) |
| Test + độ phủ backend (ngưỡng 80% dòng, chặn build) | `cd backend; ./mvnw verify` |
| Test frontend | `cd frontend; npm test` |
| Độ phủ frontend (`src/api`, `src/shared`, `src/stores`, ngưỡng 80%) | `cd frontend; npm run test:coverage` |
| Lint / format / kiểu (frontend) | `npm run lint` · `npm run format` · `npm run typecheck` |
| Build | `cd backend; ./mvnw -DskipTests package` · `cd frontend; npm run build` |

## Bản đồ thư mục
| Thư mục | Để làm gì |
|---|---|
| `backend/src/main/java/vn/minhtri/xuongin/shared/` | Dùng chung: mã lỗi, thân lỗi, xử lý lỗi, quy đổi đơn vị, CORS, cấu hình chung |
| `backend/src/main/java/vn/minhtri/xuongin/api/` | Endpoint không thuộc nghiệp vụ nào (healthcheck) |
| `backend/.../engine/` (sẽ thêm) | Thuật toán xếp, Java thuần, số nguyên 1/100 mm |
| `backend/.../nesting/` (sẽ thêm) | Tạo job, chạy nền, số liệu kết quả, controller `/nesting` |
| `backend/.../file/` (sẽ thêm) | Upload, đọc kích thước, content box, bản đồ chiếm chỗ, preview |
| `backend/.../raster/` (sẽ thêm) | Render theo dải, mặt nạ "có hình", loang nền: dùng chung cho TIF và file cắt |
| `backend/.../export/` (sẽ thêm) | Dựng PDF, TIF, file cắt, zip, dựng sẵn TIF ở nền |
| `backend/.../job/`, `storage/` (sẽ thêm) | Interface kho job, kho file + bản RAM/đĩa, dọn rác |
| `backend/src/test/java/...` | Test theo cùng cấu trúc package |
| `frontend/src/api/` | Kiểu dữ liệu theo hợp đồng API (`types.ts`, chỉ điều phối sửa), axios, chuẩn hoá lỗi, cấu hình demo |
| `frontend/src/shared/` | Quy đổi và định dạng số kiểu Việt, màu loại hình, token CSS; component dùng chung `shared/ui/` (sẽ thêm) |
| `frontend/src/features/<chuc-nang>/` | Màn hình theo chức năng: `home`, `nesting` (3 bước), `not-found` |
| `frontend/src/router/` | Định tuyến, canh gác bước 2, tiêu đề tab |
| `frontend/test/` | Vitest |
| `samples/` | File mẫu kích thước chuẩn theo tên (`generate.mjs` sinh lại); `IN6 2209.tif` chỉ có trên máy người quản lý |
| `docs/yeu-cau/`, `docs/thiet-ke/`, `docs/adr/` | Yêu cầu, thiết kế, quyết định kiến trúc |
| `.github/workflows/` | CI: lint, test, độ phủ, build mỗi PR |

## Luật riêng
- Xem `CONSTRAINTS.md` (12 bất biến, bộ nhớ, giấy phép). Giao diện: `DESIGN.md` (sẽ có sau skill giao-dien).
- App cũ để đối chiếu: repo `hoangtra02092006-ui/Push-clean-leather-file` nhánh `fix/ram-va-chieu-dai-mau`. Chỉ đọc khi kẹt, không chép nguyên khối.
- Máy chủ chung với n8n: không dừng, sửa hay khởi động lại container khác; thao tác trên máy chủ phải được người quản lý duyệt.

## Nhánh, môi trường, phát hành
- Nhánh `feat/<ten>`, `fix/<ten>`; PR vào `main`, CI phải xanh. Commit Conventional Commits, tiếng Anh.
- Môi trường: local và production. Biến môi trường: `.env.example` (backend), `frontend/.env.example`.
- Phát hành: agent chuẩn bị gói, người quản lý bấm deploy (skill phat-hanh).
