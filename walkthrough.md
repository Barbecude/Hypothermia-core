# Walkthrough: Food Freezing Commands, Inventory/Container Ticking & GUI Inventory Frozen Texture Fix

## Summary of Changes

### 1. Root Cause Analysis: Frozen Texture Missing in GUI Inventory
- **Gejala**: Tekstur beku (`frozen1`, `frozen2`, `frozen3`) terlihat jelas saat makanan dipegang di tangan (`first_person` / `third_person`) atau dijatuhkan ke tanah (`dropped item`), tetapi di slot inventory GUI (hotbar, tas inventory, peti/chest), teksturnya kembali ke tekstur makanan biasa/segar (tidak beku).
- **Penyebab Utama**:
  1. Mod `eating-animation` (`eating-animation-1.21+1.9.72.jar`) memiliki `DrawContextMixin` yang meng-intercept `GuiGraphics.renderItem` (di Yarn: `DrawContext.drawItem`).
  2. Di dalam `DrawContextMixin`, jika sebuah item memiliki komponen `DataComponents.FOOD`, ia memanggil `getHeldFoodItemModel(stack, entity, seed)` yang mengeksekusi:
     ```java
     if (stack.has(DataComponents.FOOD)) {
         return this.client.getItemRenderer().getItemModelShaper().getItemModel(stack);
     }
     ```
  3. Kode ini secara sepihak memotong jalur `model.getOverrides().resolve(...)` milik Minecraft!
  4. Model dasar dari FIAHI adalah `FIAHIBakedModel`, yang membungkus model `original`, `frozen1-3`, dan `rotten1-3`. `FIAHIBakedModel.getQuads()` secara default **selalu mengembalikan quads model `original`**, karena FIAHI mengandalkan `getOverrides().resolve(...)` untuk mengembalikan `frozen1-3`.
  5. Karena `eating-animation` membypass `getOverrides().resolve(...)` di GUI, `FIAHIBakedModel` yang belum di-resolve dikirim ke renderer GUI, sehingga yang dirender di inventory slot selalu model `original` (segar).

---

### 2. Fix Implementation: Restoring Frozen/Rotten Overrides in GUI & ItemRenderer
Kami mengimplementasikan dua lapis perbaikan client-side mixin:

1. [GuiGraphicsFoodModelMixin.java](file:///D:/Users/Zevaldo/Documents/my%20mods/Hypothermia%20core/src/client/java/com/example/client/mixin/GuiGraphicsFoodModelMixin.java):
   - Diinjeksi ke `GuiGraphics.renderItem(LivingEntity, Level, ItemStack, int, int, int, int)` dengan `@ModifyVariable` pada `STORE` ordinal 0 dan prioritas `2000` (dijalankan setelah `eating-animation` yang berprioritas 1000).
   - Memastikan `bakedModel` yang tersimpan di GUI langsung di-resolve menggunakan `model.getOverrides().resolve(...)`.
   - Mengembalikan `frozen1`, `frozen2`, atau `frozen3` ke `GuiGraphics`, sehingga pencahayaan (`usesBlockLight`), posisi 3D (`isGui3d`), dan pemanggilan `ItemRenderer.render` menggunakan model beku.

2. [ItemRendererFoodModelMixin.java](file:///D:/Users/Zevaldo/Documents/my%20mods/Hypothermia%20core/src/client/java/com/example/client/mixin/ItemRendererFoodModelMixin.java):
   - Diinjeksi ke `ItemRenderer.render(ItemStack, ItemDisplayContext, boolean, PoseStack, MultiBufferSource, int, int, BakedModel)` dengan `@ModifyVariable` pada `@At("HEAD")` `argsOnly = true` dan prioritas `2000`.
   - Menjamin bahwa konteks rendering apapun (termasuk jika ada mod GUI/HUD lain seperti ImmediatelyFast, REI, EMI, atau custom inventory screens yang memanggil `ItemRenderer.render` secara langsung) tetap me-resolve model makanan beku sebelum quads dirender.

---

### 3. Build & Deployment
- Proyek berhasil di-compile tanpa error:
  `gradlew build` -> `hypothermia_core-1.0.0.jar`
- Otomatis disalin ke:
  - `D:\Users\Zevaldo\curseforge\minecraft\Instances\Fabric 1.21\mods\hypothermia_core-1.0.0.jar`
  - `D:\Users\Zevaldo\AppData\Roaming\ModrinthApp\profiles\Fabric 1.21\mods\hypothermia_core-1.0.0.jar`
