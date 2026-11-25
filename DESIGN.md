# DESIGN.md: Xưởng in Minh Trí

Loại web: **công cụ nội bộ** (thợ và chủ xưởng dùng hằng ngày, máy tính ở xưởng là chính, điện thoại để xem nhanh).
Tham chiếu: Linear (mật độ thông tin vừa phải, viền mảnh thay bóng đổ, một màu nhấn) và Stripe Dashboard (bảng số căn phải, chữ số đều, ô số liệu gọn). Chỉ học cách phối, không lấy thương hiệu.
Màu nhấn lấy từ logo xưởng (xanh lục lam đậm), nền giấy ấm cho hợp nghề in.

Token nằm ở `frontend/src/shared/tokens.css`. Màn hình chỉ dùng biến trong đó, không đặt màu, cỡ chữ, khoảng cách lẻ.

## Màu
| Token | Giá trị | Dùng cho |
|---|---|---|
| `--color-bg` | `#F5F5F2` | nền trang (giấy ấm) |
| `--color-surface` | `#FFFFFF` | thẻ, bảng, header |
| `--color-surface-2` | `#FAFAF8` | đầu bảng, chân thẻ, ô khoá |
| `--color-border` | `#E4E3DD` | viền, đường chia |
| `--color-border-strong` | `#CFCEC6` | viền ô nhập, nút phụ |
| `--color-text` | `#1B1C1A` | chữ chính |
| `--color-muted` | `#63635C` | chữ phụ, nhãn |
| `--color-subtle` | `#8A8A82` | gợi ý, đơn vị trong ô |
| `--color-primary` | `#0F766E` | **một** màu nhấn: nút chính, link, focus, bước hiện tại |
| `--color-primary-hover` / `-soft` / `-ink` | `#115E59` / `#E7F2F0` / `#0B5650` | hover; nền nhạt (ô ước lượng, TIF sẵn sàng); chữ trên nền nhạt |
| `--color-success` (+`-soft`) | `#15803D` / `#E9F5ED` | "Tiết kiệm so với in rời", TIF đã sẵn sàng hết |
| `--color-warning` (+`-soft`) | `#A16207` / `#FDF4E3` | badge "Chế độ demo", "khoá xoay", cảnh báo không xếp lồng được |
| `--color-danger` (+`-soft`) | `#B91C1C` / `#FCECEA` | lỗi ô nhập, "Lỗi · Thử lại", hình rộng hơn khổ |
| Màu loại hình | 8 màu cố định (`category-colors.ts`, đặc tả 5.1) | chỉ cho chấm màu mẫu và khối trong preview, không dùng làm màu giao diện |

## Chữ
- Họ chữ: **Be Vietnam Pro** (Google Fonts, 400/500/600/700, dấu tiếng Việt chuẩn), dự phòng Inter, Segoe UI, system-ui.
- Thang cỡ: 12 / 13 / 14 (nội dung) / 16 / 20 / 24 (tiêu đề màn) / 32 (số liệu lớn).
- Độ đậm: 400 nội dung, 500 nhãn và nút, 600–650 tiêu đề và số liệu.
- `line-height` 1,55 cho nội dung, 1,3 cho tiêu đề (đủ chỗ cho dấu Ấ, Ổ, Ị).
- Mọi số dùng `font-variant-numeric: tabular-nums`, cột số căn phải cả tiêu đề lẫn ô. Định dạng số chỉ qua `shared/units.ts` (kiểu Việt: `25.200`, `0,3`, `4,78`).

## Khoảng cách, bo góc, bóng
- Khoảng cách: 4 / 8 / 12 / 16 / 24 / 32 / 48 (`--space-1` … `--space-7`). Gần cho thứ liên quan (nhãn–ô 4), xa giữa các khối (16–24).
- Bo góc: 6 (ô nhập, nút nhỏ), 8 (nút, hộp thoại nhỏ), 12 (thẻ, ô số liệu). Badge 4, viên thuốc (tab tấm, "Sắp có") 999.
- Bóng: thẻ **không có bóng**, chỉ viền 1 px. Bóng chỉ cho lớp nổi (toast, hộp thoại: `--shadow-overlay`) và chân trang dính (`--shadow-sticky`).
- Khung: rộng tối đa 1200 px, lề 24 px (máy tính) / 16 px (điện thoại). Header dính 60 px.

## Chuyển động
160 ms, `cubic-bezier(0.2, 0, 0, 1)`. Chỉ cho: hover/nhấn nút (màu nền, dịch 1 px khi nhấn), công tắc, toast trượt vào, thanh tiến độ. Không có hiệu ứng khi cuộn. Tôn trọng `prefers-reduced-motion`. Không dùng `transition: all`.

## Component chung (`frontend/src/shared/ui/`)
| Component | Mô tả, trạng thái |
|---|---|
| `AppButton` | biến thể `primary` (nền màu nhấn, **một nút mỗi màn**), `secondary` (nền trắng viền đậm), `ghost` (chữ xám, điều hướng phụ), `danger`; cỡ `sm`/`md`/`lg`; `loading` hiện vòng quay và khoá; tự thành `RouterLink` (`to`) hoặc `<a>` (`href`). |
| `DecimalInput` | ô số trong bảng: `type="text" inputmode="decimal"`, giữ nguyên chữ đang gõ, phát giá trị qua `parseDecimal`, nhận `2,5` và `2.5`; lúc rời ô hiện lại giá trị chuẩn; viền đỏ khi sai. |
| `NumberField` | ô số có nhãn trong form (bước 2): nhãn trên, đơn vị "cm" trong ô, dòng gợi ý dưới ô đổi thành lỗi đỏ ngay khi sai (`aria-live`). |
| `AppSwitch` | công tắc có nhãn trạng thái "Có/Không" **rộng cố định** để bảng không xô lệch. |
| `AppStepper` | 3 bước: bước đã qua là link quay lại (dấu tích), bước hiện tại tô màu nhấn, bước chưa tới là nút `disabled` thật. Trên điện thoại chỉ hiện nhãn bước hiện tại. |
| `AppCard` | thẻ trắng viền mảnh, tiêu đề + dòng phụ, `flush` cho bảng sát mép, chân thẻ xám nhạt. |
| `StatTile` | ô số liệu: nhãn nhỏ, số 32 px; `tone="primary"` (viền màu nhấn, "Tổng chiều dài"), `tone="success"` ("Tiết kiệm"). |
| `StateBlock` | khối trạng thái cả vùng: `empty` / `loading` / `error`, luôn có tiêu đề, giải thích và nút hành động tiếp theo (bất biến 12). |
| `InlineAlert` | cảnh báo trong dòng: `info` / `warning` / `danger`, tiêu đề đậm + giải thích. |
| `ToastHost` + `toast.ts` | toast góc trên phải, tự ẩn sau 4 giây, `success` / `error` / `info` (vạch màu bên trái), có nút đóng. |
| `ProgressBar` | thanh 6 px, `primary` / `success` / `danger`, có `role="progressbar"`. |
| `AppDialog` | hộp thoại giữa màn trên nền mờ, Esc hoặc bấm nền để đóng, focus vào nút đóng. |
| `AppBadge`, `AppSpinner`, `AppIcon`, `PageHeader` | badge 20 px; vòng quay; bộ icon nét 1,75 kiểu Lucide (một bộ duy nhất); tiêu đề màn + dòng phụ + nút phụ bên phải. |

Component riêng của màn ghép ở `frontend/src/features/nesting/components/`: `FileDropzone`, `ItemTable`, `StickyFooter`, `ModePicker`, `SwitchRow`, `OrderSummary`, `ByTypeTable`, `SheetTable`, `TifCell`, `TifProgressLine`, `SheetPreview`, `WizardSteps`.

## Bố cục từng màn
- **Trang chủ:** ô "Ghép file in" là thẻ lớn chiếm trọn hàng (viền màu nhấn, quy trình 3 bước, nút "Bắt đầu"); 4 ô "Sắp có" nhỏ, nền xám, chữ nhạt, khoá. Không làm 5 ô giống hệt nhau vì chỉ một ô dùng được.
- **Bước 1:** vùng kéo thả (thu gọn khi đã có file) → thẻ bảng hình → chân trang dính (tổng + "Tiếp tục →"). Điện thoại: mỗi dòng thành một thẻ nhỏ, ô số giữ nhãn.
- **Bước 2:** form trái (Khổ in; Khoảng cách & xoay) / cột phải dính (ô "Ước lượng tối thiểu", cảnh báo, "Danh sách sẽ ghép"). Dưới 960 px thành một cột. Hàng nút ở chân trang dính.
- **Bước 3:** 4 ô số (2 cột trên điện thoại) → "Từng mẫu ăn mấy mét" → "Chi tiết từng file" có dòng tiến độ TIF → "Xem trước bố trí" (tab tấm, khung cuộn 70 % chiều cao màn) → hàng nút tải. Điện thoại: hai bảng thành thẻ có nhãn, nút tải xếp dọc, "Tải tất cả TIF" trên cùng.

## Nên
- Dùng đúng câu chữ đặc tả mục 5; số liệu luôn qua `units.ts`.
- Cột số căn phải, chữ số đều; bảng có đầu bảng xám nhạt, viền dòng mảnh.
- Mỗi màn đúng một nút chính. TIF là định dạng chính nên "Tải tất cả TIF" là nút chính ở bước 3; TIF từng tấm dùng nền xanh nhạt để nổi hơn PDF/CẮT.
- Trạng thái nào cũng có chữ và hành động tiếp theo (đang tải, rỗng, lỗi, đang dựng TIF).
- Nút không khoá khi đang chờ máy chủ (TIF đang chờ vẫn bấm được để ưu tiên).
- Viền focus 2 px màu nhấn cho mọi thứ bấm được.

## Không nên
- Không dùng tím/chàm làm màu giao diện (tím đã là màu loại hình số 1), không gradient, không bóng đổ cho thẻ.
- Không dùng màu loại hình cho nút hay trạng thái, để màu đó chỉ mang nghĩa "mẫu nào".
- Không dùng `<input type="number">`.
- Không dùng emoji làm icon; không thêm icon bộ khác.
- Không căn giữa đoạn văn dài; chỉ căn giữa khối trạng thái ngắn.
- Không thêm hiệu ứng khi cuộn, không animate thuộc tính gây vẽ lại bố cục.

## Ghi chú
- Chưa có hình mẫu do người quản lý gửi; tự chọn Linear/Stripe Dashboard làm tham chiếu. Nếu người quản lý có hình mẫu khác, đổi token ở `tokens.css`, component tự theo.
- Logo lấy từ app cũ của xưởng (`frontend/src/assets/logo.png`).
