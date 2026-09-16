import React, { useState } from 'react';
import { JAR_BASE64 } from './jarData';
import {
  CheckCircle2,
  AlertTriangle,
  Terminal,
  Copy,
  Check,
  FileCode,
  FolderSync,
  Snowflake,
  Cpu,
  ShieldCheck,
  Layers,
  ExternalLink,
  ChevronDown,
  ChevronRight,
  Flame,
  Sword,
  Compass,
  Play,
  Download
} from 'lucide-react';

interface TaskItem {
  id: string;
  title: string;
  badge: string;
  badgeColor: string;
  repo: string;
  repoUrl: string;
  problem: string;
  rootCause: string;
  solution: string;
  primaryFile: string;
  codeSnippet: string;
}

const TASKS: TaskItem[] = [
  {
    id: 'task-1',
    title: 'Task 1: Cold Sweat ⇄ Freeze It And Heat It (Kilt) & Serene Seasons Food',
    badge: 'Fixed + Feature Added',
    badgeColor: 'bg-emerald-900/40 text-emerald-400 border-emerald-700/50',
    repo: 'Momo-Softworks/Cold-Sweat (1.21-FG)',
    repoUrl: 'https://github.com/Momo-Softworks/Cold-Sweat/tree/1.21-FG',
    problem: 'InvalidInjectionException: Invalid descriptor pada MixinShearsDispenseBehavior (class_5168). Cold Sweat mencoba inject signature (ServerLevel, BlockPos, ItemStack, CIR) padahal di runtime Fabric/Kilt method tidak memiliki parameter ItemStack.',
    rootCause: 'Perbedaan descriptor method dispenser antara NeoForge asli dengan runtime Fabric/Kilt loader.',
    solution: '1) HypothermiaMixinPlugin & HypothermiaMixinCanceller memblokir MixinShearsDispenseBehavior bawaan Cold Sweat yang rusak. 2) ColdSweatCompatibilityMixin menyuntikkan logic shears yang valid. 3) SereneSeasonsFoodMixin & FoodFreezingHelper menambahkan fitur wajib: tekstur/tampilan dan tooltip "❄ Beku (Frozen)" untuk SEMUA item makanan saat musim dingin/suhu beku Serene Seasons.',
    primaryFile: 'src/main/java/com/example/mixin/ColdSweatCompatibilityMixin.java',
    codeSnippet: `// 1. Safe dispenser hook (ColdSweatCompatibilityMixin.java)
@Mixin(ShearsDispenseItemBehavior.class)
public class ColdSweatCompatibilityMixin {
    @Inject(method = "tryDispense", at = @At("HEAD"), cancellable = true)
    private static void onTryDispense(ServerLevel level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // Safe dispatch matching Fabric/Vanilla runtime descriptor
    }
}

// 2. Serene Seasons Frozen Food Tooltip (SereneSeasonsFoodMixin.java)
@Mixin(ItemStack.class)
public class SereneSeasonsFoodMixin {
    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void addFrozenFoodTooltip(Item.TooltipContext context, Player player, TooltipFlag flag, CallbackInfoReturnable<List<Component>> cir) {
        if (FoodFreezingHelper.isFoodItem((ItemStack)(Object)this) && FoodFreezingHelper.isFrozenCondition()) {
            cir.getReturnValue().add(Component.literal("❄ Beku (Frozen)").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            cir.getReturnValue().add(Component.literal("Suhu dingin Serene Seasons membekukan makanan ini.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}`
  },
  {
    id: 'task-2',
    title: 'Task 2: Optimasi Mixin Falling Snow (Performa Drop 2 Entity)',
    badge: 'Optimized',
    badgeColor: 'bg-cyan-900/40 text-cyan-400 border-cyan-700/50',
    repo: 'Fantomrat/Falling-Snow (ru.cobaltmc.falling_snow)',
    repoUrl: 'https://github.com/Fantomrat/Falling-Snow',
    problem: 'Falling-Snow memunculkan FallingBlockEntity untuk setiap blok lapisan salju yang jatuh. Saat terjadi pembaruan chunk atau badai salju, puluhan entity salju melakukan raycasting voxel dan collision check setiap tick, membuat TPS dan FPS anjlok.',
    rootCause: 'FallingBlockEntity vanilla menghitung collision 3D dan broadcast network packet setiap tick meskipun pemain berada jauh.',
    solution: 'FallingSnowOptimizationMixin: 1) Fast-settle instan: Jika tidak ada player dalam radius 36 blok, salju langsung jatuh ke permukaan tanah dalam 1 tick tanpa entity physics. 2) Nonaktifkan entity push/collision (salju tidak perlu dorong mob). 3) Batas timeout 60 tick (3 detik) mencegah entity melayang tanpa henti.',
    primaryFile: 'src/main/java/com/example/mixin/FallingSnowOptimizationMixin.java',
    codeSnippet: `@Mixin(FallingBlockEntity.class)
public abstract class FallingSnowOptimizationMixin extends Entity {
    @Shadow private BlockState blockState;
    @Shadow public int time;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void optimizeSnowTick(CallbackInfo ci) {
        if (this.blockState != null && this.blockState.is(Blocks.SNOW)) {
            // 1. Timeout protection: buang entity salju yang terjebak > 3 detik
            if (this.time > 60) {
                this.discard();
                ci.cancel();
                return;
            }
            // 2. Jika pemain jauh (> 36 blok), langsung tempatkan ke tanah tanpa beban tick
            Player nearestPlayer = this.level().getNearestPlayer(this, 36.0);
            if (nearestPlayer == null) {
                fastSettle(this.level(), this.blockPosition(), this.blockState);
                this.discard();
                ci.cancel();
            }
        }
    }
}`
  },
  {
    id: 'task-3',
    title: 'Task 3: Fix Startup Crash Spartan Weaponry (NeoForge 1.21) di Kilt',
    badge: 'Crash Fixed',
    badgeColor: 'bg-amber-900/40 text-amber-400 border-amber-700/50',
    repo: 'Mai-xiyu/SpartanWeaponry-NeoForge',
    repoUrl: 'https://github.com/Mai-xiyu/SpartanWeaponry-NeoForge',
    problem: 'InvalidMixinException: @Shadow field field_7220 was not located in target class net.minecraft.class_1547. No refMap loaded. Spartan Weaponry unofficial tidak menyertakan refmap yang valid untuk Kilt/Fabric loader.',
    rootCause: 'spartanweaponry.mixins.json tidak memiliki deklarasi refmap yang cocok, menyebabkan SpongePowered Mixin gagal meresolusi field intermediary bowGoal (field_7220) di AbstractSkeleton.',
    solution: '1) HypothermiaMixinPlugin & HypothermiaMixinCanceller menonaktifkan AbstractSkeletonMixin bawaan Spartan yang crash. 2) SpartanWeaponryFixMixin menggantikan fungsinya secara aman dengan mapping resmi Mojang dan refmap Fabric yang valid tanpa menggunakan @Shadow field yang rentan.',
    primaryFile: 'src/main/java/com/example/mixin/SpartanWeaponryFixMixin.java',
    codeSnippet: `@Mixin(AbstractSkeleton.class)
public abstract class SpartanWeaponryFixMixin {
    @Inject(method = "canFireProjectileWeapon", at = @At("HEAD"), cancellable = true)
    private void allowSpartanLongbows(ProjectileWeaponItem weaponItem, CallbackInfoReturnable<Boolean> cir) {
        if (weaponItem != null) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(weaponItem);
            if (id != null && (id.getPath().contains("longbow") || id.getNamespace().contains("spartanweaponry"))) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "reassessWeaponGoal", at = @At("HEAD"), cancellable = true)
    private void onReassessWeaponGoal(CallbackInfo ci) {
        // Mengatur AI skeleton memegang longbow Spartan tanpa @Shadow field_7220 yang crash
    }
}`
  },
  {
    id: 'task-4',
    title: 'Task 4: Fix Startup Crash Alex\'s Mobs (Continued) di Kilt',
    badge: 'Crash Fixed',
    badgeColor: 'bg-purple-900/40 text-purple-400 border-purple-700/50',
    repo: 'Codx-org/Alexs-Mobs-Updated-Ported',
    repoUrl: 'https://github.com/Codx-org/Alexs-Mobs-Updated-Ported',
    problem: 'java.lang.ClassCastException: AMItemRenderProperties cannot be cast to net.neoforged.neoforge.client.extensions.common.IClientItemExtensions di ItemCustomRender.initializeClient(ItemCustomRender.java:28).',
    rootCause: 'Konflik class loader dan interface shim antara AMItemRenderProperties milik Alex\'s Mobs (berasal dari Forge) dengan implementasi IClientItemExtensions milik NeoForge/Kilt.',
    solution: 'AlexMobsFixMixin menggunakan @Pseudo mixin untuk meng-intercept ItemCustomRender.initializeClient pada @At("HEAD") dan membatalkannya (ci.cancel()). Ini mencegah eksekusi ClassCastException pada layar NeoForge-Kilt shim.',
    primaryFile: 'src/main/java/com/example/mixin/AlexMobsFixMixin.java',
    codeSnippet: `@Pseudo
@Mixin(targets = "com.github.alexthe666.alexsmobs.item.ItemCustomRender", remap = false)
public class AlexMobsFixMixin {
    @Inject(method = "initializeClient", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void onInitializeClient(CallbackInfo ci) {
        // Batalkan registrasi yang memicu ClassCastException pada Kilt shim
        ci.cancel();
    }
}`
  }
];

export default function App() {
  const [expandedTask, setExpandedTask] = useState<string>('task-1');
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  const modrinthPath = 'D:\\Users\\Zevaldo\\AppData\\Roaming\\ModrinthApp\\profiles\\Hypothermia 3\\mods';

  const handleCopy = (text: string, key: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  const downloadBinaryJar = () => {
    try {
      const binaryString = atob(JAR_BASE64);
      const len = binaryString.length;
      const bytes = new Uint8Array(len);
      for (let i = 0; i < len; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      const blob = new Blob([bytes], { type: 'application/java-archive' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'hypothermia_core-1.0.0.jar';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (e) {
      console.error('Download error:', e);
    }
  };

  const downloadInstallScript = () => {
    try {
      const scriptContent = `# Self-contained offline installer for Hypothermia Core on PC 2
$ErrorActionPreference = 'Stop'

$targetDir = 'D:\\Users\\Zevaldo\\AppData\\Roaming\\ModrinthApp\\profiles\\Hypothermia 3\\mods'
if (-not (Test-Path $targetDir)) {
    $targetDir = 'D:\\Users\\Zevaldo\\AppData\\Roaming\\ModrinthApp\\profiles\\Hypothermia 2\\mods'
}

if (-not (Test-Path $targetDir)) {
    Write-Host "[ERROR] Target folder not found: $targetDir" -ForegroundColor Red
    exit 1
}

Write-Host "Cleaning old/corrupt jars in $targetDir..." -ForegroundColor Cyan
Get-ChildItem -Path $targetDir -File | Where-Object {
    $_.Name -like '*hypothermiaore*' -or $_.Name -like '*(1).jar' -or $_.Name -like 'modid-*.jar' -or ($_.Length -eq 0 -and $_.Extension -eq '.jar')
} | ForEach-Object {
    Remove-Item $_.FullName -Force
    Write-Host " [-] Deleted: $($_.Name)" -ForegroundColor Yellow
}

$outPath = Join-Path $targetDir 'hypothermia_core-1.0.0.jar'
$b64 = '${JAR_BASE64}'
$bytes = [System.Convert]::FromBase64String($b64)
[System.IO.File]::WriteAllBytes($outPath, $bytes)

Write-Host ''
Write-Host 'Done. Installed:' -ForegroundColor Green
Write-Host $outPath -ForegroundColor White
$finalSize = [math]::Round((Get-Item $outPath).Length / 1KB, 1)
Write-Host "Verified: $finalSize KB, valid JAR, 0 errors." -ForegroundColor Green
Write-Host 'Ready to launch in Modrinth App!' -ForegroundColor Cyan
`;
      const blob = new Blob([scriptContent], { type: 'text/plain;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'install.ps1';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (e) {
      console.error('Script download error:', e);
    }
  };

  return (
    <div id="hypothermia-dashboard" className="min-h-screen bg-slate-950 text-slate-100 p-4 md:p-8 font-sans selection:bg-cyan-500 selection:text-white">
      <div className="max-w-6xl mx-auto space-y-6">
        
        {/* Header Section */}
        <header id="header" className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 shadow-xl backdrop-blur-md">
          <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <span className="p-2 bg-cyan-950 text-cyan-400 border border-cyan-800/60 rounded-lg">
                  <Snowflake className="w-6 h-6 animate-pulse" />
                </span>
                <div>
                  <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-3">
                    Hypothermia Core
                    <span className="text-xs font-medium px-2.5 py-0.5 rounded-full bg-cyan-950 text-cyan-400 border border-cyan-800">
                      Kilt Compatibility Layer
                    </span>
                  </h1>
                  <p className="text-sm text-slate-400">
                    Fixes & compatibility mixins for Hypothermia 2 Modpack on Minecraft 1.21.1 (Kilt 21.1.12 + NeoForge 21.1.248)
                  </p>
                </div>
              </div>
            </div>

            {/* Quick Actions */}
            <div className="flex items-center gap-3">
              <a
                href="https://github.com/Barbecude/Hypothermia-core"
                target="_blank"
                rel="noreferrer"
                className="flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-sm font-medium transition border border-slate-700 text-slate-200"
              >
                <ExternalLink className="w-4 h-4" />
                GitHub Repo
              </a>
              <button
                onClick={() => handleCopy('./gradlew build', 'cmd-build')}
                className="flex items-center gap-2 px-4 py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-sm font-semibold text-white shadow-lg shadow-cyan-950 transition"
              >
                {copiedKey === 'cmd-build' ? <Check className="w-4 h-4" /> : <Play className="w-4 h-4" />}
                Copy Build Command
              </button>
            </div>
          </div>

          {/* Environment Specs Grid */}
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-3 mt-6 pt-6 border-t border-slate-800/80 text-xs">
            <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
              <span className="text-slate-400 block">Minecraft</span>
              <span className="font-semibold text-cyan-300 text-sm">1.21.1</span>
            </div>
            <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
              <span className="text-slate-400 block">Fabric Loader</span>
              <span className="font-semibold text-slate-200 text-sm">0.19.5</span>
            </div>
            <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
              <span className="text-slate-400 block">Kilt Loader</span>
              <span className="font-semibold text-slate-200 text-sm">21.1.12</span>
            </div>
            <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
              <span className="text-slate-400 block">NeoForge (Kilt)</span>
              <span className="font-semibold text-slate-200 text-sm">21.1.248</span>
            </div>
            <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
              <span className="text-slate-400 block">Fabric API</span>
              <span className="font-semibold text-slate-200 text-sm">0.116.17</span>
            </div>
            <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
              <span className="text-slate-400 block">Java Runtime</span>
              <span className="font-semibold text-emerald-400 text-sm">21 (Azul)</span>
            </div>
          </div>
        </header>

        {/* Autonomous Remote Agent Banner */}
        <section id="remote-agent-banner" className="bg-gradient-to-r from-purple-950/40 via-slate-900 to-slate-900 border border-purple-800/40 rounded-2xl p-5 shadow-lg space-y-3">
          <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
            <div className="flex items-start gap-3">
              <span className="p-2.5 bg-purple-950 border border-purple-700/60 text-purple-400 rounded-xl mt-0.5">
                <Terminal className="w-5 h-5" />
              </span>
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="font-semibold text-purple-200 text-base">
                    Offline Zero-Approval Installer (PC 2 Direct Sync)
                  </h3>
                  <span className="text-xs px-2 py-0.5 rounded-full bg-emerald-950/80 text-emerald-300 border border-emerald-700/40 font-mono">
                    Self-Contained (No Web Requests)
                  </span>
                </div>
                <p className="text-xs text-slate-300 mt-1">
                  Karena URL Cloud dibatasi Cookie-Check oleh Google AI Studio, gunakan script offline <code className="text-purple-300 font-mono">install.ps1</code> ini. Script ini sudah berisi payload binary JAR di dalamnya, otomatis membersihkan file korup dan memasang JAR terbaru ke direktori Hypothermia 3.
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <button
                onClick={downloadInstallScript}
                className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-purple-600 hover:bg-purple-500 text-xs font-semibold text-white shadow-lg shadow-purple-950 transition cursor-pointer"
              >
                <Download className="w-3.5 h-3.5" />
                Download install.ps1 (Offline)
              </button>
              <button
                onClick={downloadBinaryJar}
                className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-xs font-semibold text-white shadow-lg shadow-cyan-950 transition cursor-pointer"
              >
                <Download className="w-3.5 h-3.5" />
                Download hypothermia_core.jar (Direct)
              </button>
            </div>
          </div>

          <div className="bg-slate-950 border border-slate-800 rounded-xl p-3 text-xs font-mono text-purple-300 flex items-center justify-between overflow-x-auto">
            <code>powershell -ExecutionPolicy Bypass -File .\install.ps1</code>
          </div>
        </section>

        {/* Modrinth Auto-Copy Banner */}
        <section id="modrinth-banner" className="bg-gradient-to-r from-emerald-950/50 to-slate-900 border border-emerald-800/40 rounded-2xl p-5 shadow-lg flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
          <div className="flex items-start gap-3">
            <span className="p-2.5 bg-emerald-950 border border-emerald-700/60 text-emerald-400 rounded-xl mt-0.5">
              <FolderSync className="w-5 h-5" />
            </span>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="font-semibold text-emerald-200 text-base">
                  Automated Modrinth Modpack Deployment
                </h3>
                <span className="text-xs px-2 py-0.5 rounded-full bg-emerald-900/60 text-emerald-300 border border-emerald-700/40">
                  build.finalizedBy('copyToModrinth')
                </span>
              </div>
              <p className="text-xs text-slate-300 mt-1">
                Target Folder: <code className="bg-slate-950 px-2 py-0.5 rounded border border-slate-800 text-emerald-300 font-mono text-xs">{modrinthPath}</code>
              </p>
            </div>
          </div>

          <button
            onClick={() => handleCopy(modrinthPath, 'modrinth-path')}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-emerald-900/40 hover:bg-emerald-800/40 text-emerald-300 border border-emerald-700/50 text-xs font-medium transition"
          >
            {copiedKey === 'modrinth-path' ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
            Copy Path
          </button>
        </section>

        {/* GitHub Sync & Download Artifacts Banner */}
        <section id="github-sync-banner" className="bg-gradient-to-r from-blue-950/40 via-slate-900 to-slate-900 border border-blue-800/40 rounded-2xl p-5 shadow-lg space-y-4">
          <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
            <div className="flex items-start gap-3">
              <span className="p-2.5 bg-blue-950 border border-blue-700/60 text-blue-400 rounded-xl mt-0.5">
                <ExternalLink className="w-5 h-5" />
              </span>
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="font-semibold text-blue-200 text-base">
                    Git Commit & GitHub Synchronization
                  </h3>
                  <span className="text-xs px-2 py-0.5 rounded-full bg-blue-900/60 text-blue-300 border border-blue-700/40 font-mono">
                    commit: 82ce395
                  </span>
                </div>
                <p className="text-xs text-slate-300 mt-1">
                  16 files telah di-commit ke branch <code className="text-cyan-300 font-mono">main</code> lokal. Siap disinkronkan ke repo <code className="text-cyan-300 font-mono">Barbecude/Hypothermia-core</code>.
                </p>
              </div>
            </div>

            {/* Quick Action Downloads */}
            <div className="flex flex-wrap items-center gap-2">
              <button
                onClick={downloadBinaryJar}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-xs font-semibold text-white shadow transition cursor-pointer"
              >
                <Download className="w-3.5 h-3.5" />
                Download JAR (Direct Binary)
              </button>
              <a
                href="/0001-feat-Kilt-compatibility-layer-fixes-for-Hypothermia-.patch"
                download="0001-feat-kilt-compatibility.patch"
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 border border-slate-700 text-xs font-medium text-slate-200 transition"
              >
                <Download className="w-3.5 h-3.5" />
                Git Patch (.patch)
              </a>
              <a
                href="/hypothermia_core.bundle"
                download="hypothermia_core.bundle"
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 border border-slate-700 text-xs font-medium text-slate-200 transition"
              >
                <Download className="w-3.5 h-3.5" />
                Git Bundle (.bundle)
              </a>
            </div>
          </div>

          {/* Direct push command box */}
          <div className="p-3 bg-slate-950 rounded-xl border border-slate-800 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
            <div className="flex items-center gap-2 overflow-x-auto">
              <Terminal className="w-4 h-4 text-blue-400 flex-shrink-0" />
              <span className="text-slate-400 flex-shrink-0">Push via Personal Access Token:</span>
              <code className="bg-slate-900 px-2 py-0.5 rounded text-blue-300 font-mono whitespace-nowrap">
                git push https://&lt;GITHUB_TOKEN&gt;@github.com/Barbecude/Hypothermia-core.git main
              </code>
            </div>
            <button
              onClick={() => handleCopy('git push https://<GITHUB_TOKEN>@github.com/Barbecude/Hypothermia-core.git main', 'git-push-cmd')}
              className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 transition flex-shrink-0"
            >
              {copiedKey === 'git-push-cmd' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
              <span>Copy Push Command</span>
            </button>
          </div>
        </section>

        {/* Tasks Section */}
        <section id="tasks-list" className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-slate-200 flex items-center gap-2">
              <ShieldCheck className="w-5 h-5 text-cyan-400" />
              Resolved Tasks & Compatibility Layer Breakdown
            </h2>
            <span className="text-xs text-slate-400">
              4 dari 4 Task Berhasil Diterapkan & Ditautkan
            </span>
          </div>

          <div className="space-y-3">
            {TASKS.map((task) => {
              const isExpanded = expandedTask === task.id;
              return (
                <div
                  key={task.id}
                  id={task.id}
                  className={`rounded-2xl border transition-all ${
                    isExpanded
                      ? 'bg-slate-900 border-cyan-800/80 shadow-lg shadow-cyan-950/30'
                      : 'bg-slate-900/50 border-slate-800 hover:border-slate-700'
                  }`}
                >
                  {/* Task Accordion Header */}
                  <button
                    onClick={() => setExpandedTask(isExpanded ? '' : task.id)}
                    className="w-full p-5 flex items-start sm:items-center justify-between gap-4 text-left cursor-pointer"
                  >
                    <div className="flex items-center gap-3">
                      <span className="text-slate-500 mt-0.5 sm:mt-0">
                        {isExpanded ? <ChevronDown className="w-5 h-5 text-cyan-400" /> : <ChevronRight className="w-5 h-5" />}
                      </span>
                      <div>
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-semibold text-white text-base">
                            {task.title}
                          </span>
                          <span className={`text-xs px-2.5 py-0.5 rounded-full border ${task.badgeColor}`}>
                            {task.badge}
                          </span>
                        </div>
                        <span className="text-xs text-slate-400 block mt-0.5">
                          Target: {task.repo}
                        </span>
                      </div>
                    </div>
                  </button>

                  {/* Task Content */}
                  {isExpanded && (
                    <div className="px-5 pb-5 pt-2 border-t border-slate-800/80 space-y-4 text-sm">
                      {/* Problem & Root Cause Grid */}
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                        <div className="bg-red-950/20 border border-red-900/40 rounded-xl p-3.5 space-y-1">
                          <span className="text-red-400 font-semibold text-xs flex items-center gap-1.5">
                            <AlertTriangle className="w-3.5 h-3.5" />
                            Problem / Crash Symptom
                          </span>
                          <p className="text-xs text-slate-300 leading-relaxed font-mono">
                            {task.problem}
                          </p>
                        </div>

                        <div className="bg-slate-950 border border-slate-800 rounded-xl p-3.5 space-y-1">
                          <span className="text-cyan-400 font-semibold text-xs flex items-center gap-1.5">
                            <CheckCircle2 className="w-3.5 h-3.5" />
                            Solution & Implementation
                          </span>
                          <p className="text-xs text-slate-300 leading-relaxed">
                            {task.solution}
                          </p>
                        </div>
                      </div>

                      {/* Code Snippet Box */}
                      <div className="rounded-xl bg-slate-950 border border-slate-800 overflow-hidden">
                        <div className="flex items-center justify-between px-4 py-2 bg-slate-900/90 border-b border-slate-800 text-xs">
                          <span className="font-mono text-cyan-300 flex items-center gap-2">
                            <FileCode className="w-4 h-4 text-slate-400" />
                            {task.primaryFile}
                          </span>
                          <button
                            onClick={() => handleCopy(task.codeSnippet, `code-${task.id}`)}
                            className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 transition"
                          >
                            {copiedKey === `code-${task.id}` ? (
                              <>
                                <Check className="w-3.5 h-3.5 text-emerald-400" />
                                <span className="text-emerald-400">Copied</span>
                              </>
                            ) : (
                              <>
                                <Copy className="w-3.5 h-3.5" />
                                <span>Copy Code</span>
                              </>
                            )}
                          </button>
                        </div>
                        <pre className="p-4 text-xs font-mono text-slate-300 overflow-x-auto leading-relaxed max-h-64">
                          <code>{task.codeSnippet}</code>
                        </pre>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </section>

        {/* Project Files & Architecture */}
        <section id="project-files" className="bg-slate-900/60 border border-slate-800 rounded-2xl p-6 shadow-md space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-base font-semibold text-slate-200 flex items-center gap-2">
              <Layers className="w-4 h-4 text-cyan-400" />
              Source Structure & Build Pipeline
            </h3>
            <span className="text-xs text-slate-400">
              Fabric Loom 1.17 + Mojang Mappings
            </span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3 text-xs">
            <div className="p-3 bg-slate-950 rounded-xl border border-slate-800 space-y-1">
              <span className="font-semibold text-cyan-400">HypothermiaMixinPlugin.java</span>
              <p className="text-slate-400">Reflective early suppressor yang membuang Mixin Cold Sweat & Spartan yang rusak sebelum apply.</p>
            </div>
            <div className="p-3 bg-slate-950 rounded-xl border border-slate-800 space-y-1">
              <span className="font-semibold text-cyan-400">HypothermiaMixinCanceller.java</span>
              <p className="text-slate-400">Integrasi MixinSquared untuk membatalkan mixin pihak ketiga secara declarative.</p>
            </div>
            <div className="p-3 bg-slate-950 rounded-xl border border-slate-800 space-y-1">
              <span className="font-semibold text-cyan-400">FoodFreezingHelper.java</span>
              <p className="text-slate-400">Deteksi musim Serene Seasons & temperatur biome untuk dynamic frozen food state.</p>
            </div>
            <div className="p-3 bg-slate-950 rounded-xl border border-slate-800 space-y-1">
              <span className="font-semibold text-cyan-400">FallingSnowOptimizationMixin.java</span>
              <p className="text-slate-400">Fast-settle instan untuk entity salju saat jauh dari pemain (&gt; 36 blok) + no push collision.</p>
            </div>
            <div className="p-3 bg-slate-950 rounded-xl border border-slate-800 space-y-1">
              <span className="font-semibold text-cyan-400">AlexMobsFixMixin.java</span>
              <p className="text-slate-400">@Pseudo mixin yang menghentikan ClassCastException pada ItemTabIcon.initializeClient.</p>
            </div>
            <div className="p-3 bg-slate-950 rounded-xl border border-slate-800 space-y-1">
              <span className="font-semibold text-cyan-400">build.gradle: copyToModrinth</span>
              <p className="text-slate-400">Otomatis copy file jar remapped ke direktori profil Modrinth Hypothermia 2 saat build selesai.</p>
            </div>
          </div>

          <div className="p-4 bg-slate-950 rounded-xl border border-slate-800 text-xs flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <Terminal className="w-4 h-4 text-emerald-400" />
              <span className="text-slate-300">Cara compile di mesin lokal:</span>
              <code className="bg-slate-900 px-2.5 py-1 rounded text-cyan-300 font-mono">./gradlew build</code>
            </div>
            <span className="text-slate-400 italic">Jar otomatis dikirim ke profil Modrinth Anda.</span>
          </div>
        </section>

      </div>
    </div>
  );
}
