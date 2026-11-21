# KHOA AUTOMATION BOT (Fabric, Minecraft 1.21.1)

Mod **client-side**: Auto Miner, Block Scanner, Item Collector, Litematica Builder, Bot Manager.

## TRẠNG THÁI THỰC TẾ (đọc trước)
- Mã nguồn được viết **mà chưa biên dịch và chưa chạy thử** (môi trường tạo mã không có mạng nên không tải được Minecraft/Fabric).
  Khi build lần đầu có thể có vài lỗi biên dịch nhỏ do khác biệt tên API (Yarn 1.21.1). Gửi log lỗi lại để sửa.
- **Chưa có file `.jar`**. Bạn cần tự build (xem bên dưới).
- Chưa kiểm thử trong game: pathfinding, đào, nhặt, đặt block, tích hợp Litematica đều cần test thực tế.

## Cài đặt / build
Yêu cầu: JDK 21, Gradle 8.10+ (hoặc copy `gradlew` + `gradle/wrapper` từ Fabric example mod).
```
cd khoa-automation-bot
gradle wrapper --gradle-version 8.10     # nếu chưa có gradlew
./gradlew build                          # Windows: gradlew.bat build
```
File mod: `build/libs/khoa-automation-bot-1.0.0.jar` → bỏ vào thư mục `mods/` cùng Fabric Loader 0.16+ và Fabric API 0.115.0+1.21.1.
Litematica (+ MaLiLib) là tuỳ chọn, chỉ cần cho tab Litematica Builder.

## Mở menu
- `SHIFT + chuột phải` **khi tay trống** (tắt được trong Settings), hoặc
- Phím tắt (mặc định `K`, đổi được trong Settings). Bấm lại phím để đóng.

## Hướng dẫn từng chức năng
- **Dashboard**: trạng thái, FPS, thống kê, Start/Pause/Stop từng hệ thống, bật/tắt từng hệ thống.
- **Auto Miner**: tìm block ở ô Search, click để chọn nhiều loại → Start. Bot quét (từng đợt nhỏ), đi tới block,
  chọn công cụ tốt nhất trên hotbar, bỏ qua công cụ sắp hỏng, đào, rồi nhặt đồ rơi. "Chỉ đào block đã chọn" sẽ bỏ qua block bị vật cản che.
  Bot chỉ đi bộ/nhảy/rơi (không tự đào đường), nên chỉ lấy được block có đường tới.
- **Block Scanner**: quét khối vuông bán kính R quanh bạn theo từng đợt, thống kê số lượng theo loại, tìm theo tên.
  Click một dòng để thêm vào danh sách Auto Miner. Chỉ chạy khi đang mở tab này.
- **Item Collector**: danh sách NHẶT (ưu tiên) và BỎ QUA. Khi túi đầy: vứt đồ trong danh sách bỏ qua; nếu vẫn đầy thì tạm dừng.
- **Litematica Builder**: trong Litematica, load schematic và **chọn placement** (đang dùng). Mở tab → Start.
  Phân tích toàn bộ volume (hiện % tiến độ, vật liệu thiếu/tổng/đang có), chia Layer theo trục Y, Prev/Next Layer,
  chế độ "Tự xây toàn bộ" hoặc "Từng layer". Thiếu vật liệu sẽ tạm dừng → bổ sung rồi Resume.
- **Bot Manager**: tạo bot (tên + loại nhiệm vụ), Start/Pause/Stop/Xoá, xem trạng thái và tiến độ.
  Chỉ có MỘT người chơi thật nên các bot đang chạy xếp hàng thay phiên nhau (không giả lập nhiều người chơi).
- **Settings**: phạm vi quét/đào/nhặt, độ trễ, chế độ (Safe/Normal/Fast), phím tắt, ngôn ngữ VI/EN. Lưu tại `config/khoa_automation_bot.json`.

## Giới hạn đã biết
- Litematica được gọi bằng **reflection** (`SchematicWorldHandler`, `DataManager`, `getEnclosingBox`). Nếu phiên bản Litematica của bạn đổi tên API,
  tab sẽ báo lỗi cụ thể thay vì crash. Schematic world của Litematica chỉ có dữ liệu ở các chunk gần người chơi → di chuyển rồi bấm "Phân tích lại".
- Builder chỉ so khớp **loại block**, **không** tái tạo hướng/thuộc tính (stairs, slab trên/dưới, door...). Không tự phá block sai.
- Không có đóng/mở cửa, leo thang, bơi, parkour trong pathfinding.
- Bot tự dừng khi máu ≤ 4 hồi.

## Lưu ý sử dụng
Chỉ dùng trên thế giới đơn hoặc server mà bạn được phép. Nhiều server cấm mod tự động hoá (anti-cheat có thể ban). Mod chỉ dùng thao tác hợp lệ
(đào, đặt block, nhặt đồ) nhưng bạn tự chịu trách nhiệm theo luật của server.
