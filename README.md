# Xưởng in Minh Trí

Web xếp bản in DTF lên cuộn khổ cố định và xuất file sản xuất: PDF vector, TIF cho máy in (CMYK + mực trắng W1), file cắt cho Graphtec.

## Chạy trên Windows (PowerShell), từ máy trống

1. Cài **JDK 21** (Temurin: https://adoptium.net) và **Node.js 20+** (https://nodejs.org). Không cần cài Maven.
2. Clone và chạy backend:
   ```powershell
   git clone https://github.com/hoangtra02092006-ui/xuong-in-minh-tri.git
   cd xuong-in-minh-tri\backend
   .\mvnw.cmd spring-boot:run
   ```
   Mở http://localhost:8080/api/v1/health thấy `"status":"UP"` là được.
3. Mở cửa sổ PowerShell khác, chạy frontend:
   ```powershell
   cd xuong-in-minh-tri\frontend
   copy .env.example .env.local
   # sửa .env.local: VITE_API_BASE_URL=http://localhost:8080
   npm ci
   npm run dev
   ```
   Mở http://localhost:5173. Để trống `VITE_API_BASE_URL` thì app chạy chế độ demo, không cần backend.

## Test
```powershell
cd backend;  .\mvnw.cmd verify          # test + độ phủ
cd frontend; npm run test:coverage; npm run lint; npm run build
```

## Hồ sơ màu và file mẫu TIF
Không có trong repo (ADR-004). Đặt `USWebCoatedSWOP.icc` ở một thư mục trên máy, trỏ biến `APP_TIFF_CMYK_PROFILE` tới đó; đặt `IN6 2209.tif` vào `samples/` để chạy đủ test TIF.

Tài liệu: `CLAUDE.md` (bản đồ thư mục, lệnh), `docs/`.
