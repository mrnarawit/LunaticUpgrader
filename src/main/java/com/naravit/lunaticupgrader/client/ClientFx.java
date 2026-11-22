package com.naravit.lunaticupgrader.client;

import java.util.ArrayList;
import java.util.List;

import com.naravit.lunaticupgrader.ClientConfig;
import com.naravit.lunaticupgrader.LunaticUpgrader;
import com.naravit.lunaticupgrader.NameStyle;
import com.naravit.lunaticupgrader.SparkOptions;
import com.naravit.lunaticupgrader.UpgradeFx;
import com.naravit.lunaticupgrader.UpgradeFxPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * เอฟเฟกต์ Star seal ในโลก (ฝั่ง client)
 *
 * วงดาว 10 ดวงลอยเหนือ Anvil (หันหน้าเข้าหากล้องของแต่ละคนเสมอ)
 * ดาวแต่ละดวง = ขั้นที่ได้มาแล้ว สีตามขั้นนั้น
 *  - ตีสำเร็จ: ประกายรวมตัวแล้วดาวดวงใหม่สว่างขึ้น
 *  - +10: ประกายไหลเข้าช่องสุดท้าย 1.8 วินาที -> ดาวครบวง -> ระเบิดแสง
 *         (แสงวาบ คลื่นกระแทกตามพื้น เสาแสง ละอองลอยค้าง)
 *  - พลาด: ประกายรวมตัวไม่สำเร็จ ฟุ้งเป็นสีเทา และดาวดวงล่าสุดดับลง
 * คนที่ตีเองจะเห็นแสงวาบทั้งจอ และจอสั่นตอน +10 (ปิดได้ใน client config)
 */
@EventBusSubscriber(modid = LunaticUpgrader.MODID, value = Dist.CLIENT)
public final class ClientFx {
    private static final List<Fx> ACTIVE = new ArrayList<>();

    // สถานะเอฟเฟกต์บนจอ (เฉพาะคนที่ตี)
    private static float flash, shake;
    private static int shakeTicks;

    // สี
    private static final int WHITE = 0xFFFFFF, BLUE = 0x9EC3FF, PALE = 0xC9D8FF, GRAY = 0x6E6A7A;

    private ClientFx() {}

    public static void start(UpgradeFxPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean self = mc.player != null && mc.player.getId() == payload.actor();
        ACTIVE.add(new Fx(payload, self && ClientConfig.screenEffects()));
        if (self) AnvilUiFx.start(payload);
    }

    // ------------------------------------------------------------------
    // tick + เอฟเฟกต์บนจอ
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            flash = 0;
            shakeTicks = 0;
            return;
        }
        if (mc.isPaused()) return;
        ACTIVE.removeIf(fx -> !fx.tick(mc, mc.level));
        flash *= 0.82f;
        if (flash < 0.01f) flash = 0;
        if (shakeTicks > 0) shakeTicks--;
    }

    /** แสงวาบทั้งจอ (ตอนไม่มีหน้าจออื่นเปิดอยู่) */
    @SubscribeEvent
    public static void onGuiPost(RenderGuiEvent.Post event) {
        if (flash > 0.01f && Minecraft.getInstance().screen == null) {
            fillScreen(event.getGuiGraphics(), flash * 0.85f, 0xFFFFFF);
        }
    }

    /** แสงวาบทับหน้าจอที่เปิดอยู่ (เช่น หน้าจอ Anvil) */
    @SubscribeEvent
    public static void onScreenPost(ScreenEvent.Render.Post event) {
        if (flash > 0.01f) fillScreen(event.getGuiGraphics(), flash * 0.85f, 0xFFFFFF);
    }

    /** จอสั่น */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shakeTicks <= 0) return;
        float s = shake * Math.min(1f, shakeTicks / 6f);
        event.setYaw(event.getYaw() + (float) (Math.random() * 2 - 1) * s);
        event.setPitch(event.getPitch() + (float) (Math.random() * 2 - 1) * s);
        event.setRoll(event.getRoll() + (float) (Math.random() * 2 - 1) * s * 0.5f);
    }

    static void fillScreen(GuiGraphics g, float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | rgb);
        g.pose().popPose();
    }

    private static void shake(float amount, int ticks) {
        if (shakeTicks <= 0 || amount >= shake) shake = amount;
        shakeTicks = Math.max(shakeTicks, ticks);
    }

    // ------------------------------------------------------------------
    // เอฟเฟกต์ 1 ครั้ง
    // ------------------------------------------------------------------

    private static final class Fx {
        static final double RADIUS = 1.25;      // รัศมีวงดาว (บล็อก)
        static final double SEAL_HEIGHT = 1.35; // ความสูงวงดาวเหนือ Anvil
        static final int T_IGNITE = 17;         // จังหวะดาวสว่างตอนตีสำเร็จ (ตรงกับเสียง 0.85 วินาที)
        static final int T_COMPLETE = UpgradeFx.UNSEAL_DELAY; // ดาวครบวงตอน +10 (1.8 วินาที)
        static final int T_FIZZLE = 10;         // จังหวะประกายฟุ้งตอนพลาด

        final int kind, level, c0, c1, c2;
        final boolean screen;
        final Vec3 top, ground, seal;
        final RandomSource rnd = RandomSource.create();
        final List<double[]>[] pieces;
        final List<double[]> core = new ArrayList<>();
        int t;

        @SuppressWarnings("unchecked")
        Fx(UpgradeFxPayload p, boolean screen) {
            this.kind = p.kind();
            this.level = p.level();
            this.c0 = p.c0();
            this.c1 = p.c1();
            this.c2 = p.c2();
            this.screen = screen;
            Vec3 bottom = Vec3.atBottomCenterOf(p.pos());
            this.ground = bottom.add(0, 0.05, 0);
            this.top = bottom.add(0, 1.0, 0);
            this.seal = top.add(0, SEAL_HEIGHT, 0);

            // ดาว 10 ดวง: จุดกลาง + แขน 4 จุด + เส้นไปดาวถัดไป + เส้นเข้าหาแกนกลาง  (u = แนวนอน, v = แนวตั้ง, s = ขนาด)
            pieces = new List[10];
            for (int i = 0; i < 10; i++) {
                List<double[]> pts = new ArrayList<>();
                double[] a = star(i), b = star((i + 1) % 10);
                pts.add(new double[]{a[0], a[1], 1.8});
                double o = 0.12;
                pts.add(new double[]{a[0] - o, a[1], 0.9});
                pts.add(new double[]{a[0] + o, a[1], 0.9});
                pts.add(new double[]{a[0], a[1] - o, 0.9});
                pts.add(new double[]{a[0], a[1] + o, 0.9});
                for (int k = 1; k <= 3; k++) {
                    double f = k / 4.0;
                    pts.add(new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, 0.6});
                }
                double in = 0.3 / RADIUS;
                for (int k = 1; k <= 4; k++) {
                    double f = k / 5.0 * (1 - in);
                    pts.add(new double[]{a[0] * (1 - f), a[1] * (1 - f), 0.5});
                }
                pieces[i] = pts;
            }
            for (int k = 0; k < 12; k++) {
                double ang = k / 12.0 * Math.PI * 2;
                core.add(new double[]{Math.cos(ang) * 0.3, Math.sin(ang) * 0.3, 0.9});
            }
        }

        static double[] star(int i) {
            double a = -Math.PI / 2 + i * Math.PI * 2 / 10;
            return new double[]{Math.cos(a) * RADIUS, -Math.sin(a) * RADIUS};
        }

        /** สีของดาวดวงที่ i (0 = ขั้น +1) */
        int starColor(int i) {
            return i < 9 ? NameStyle.levelColor(i + 1) : c0;
        }

        /** false = จบแล้ว */
        boolean tick(Minecraft mc, ClientLevel lvl) {
            boolean particles = ClientConfig.particles();
            Vec3 right = rightVector(mc);
            boolean alive = switch (kind) {
                case UpgradeFx.SUCCESS -> success(lvl, right, particles);
                case UpgradeFx.UNSEAL -> complete(lvl, right, particles);
                default -> fail(lvl, right, particles);
            };
            t++;
            return alive;
        }

        // ---------------- ตีสำเร็จ: ดาวดวงใหม่สว่างขึ้น ----------------
        boolean success(ClientLevel lvl, Vec3 right, boolean particles) {
            int lit = Math.max(1, Math.min(9, level)); // จำนวนดาวหลังตีสำเร็จ
            int idx = lit - 1;                          // ดาวดวงใหม่
            if (!particles) return t < 36;
            if (t < 36 && t % 3 == 0) {
                drawRing(lvl, right, lit - 1, 6);
                if (t >= T_IGNITE) drawStar(lvl, right, idx, starColor(idx), 6);
                else drawGhost(lvl, right, idx, starColor(idx), t / (double) T_IGNITE);
            }
            if (t < T_IGNITE) {
                double[] s = star(idx);
                gather(lvl, plane(right, s[0], s[1]), 2, 0.7, new int[]{starColor(idx), WHITE}, 8);
            }
            if (t == T_IGNITE) {
                double[] s = star(idx);
                burst(lvl, plane(right, s[0], s[1]), 18, 0.14, new int[]{starColor(idx), WHITE}, 12, 1.0f, 0f);
            }
            return t < 38;
        }

        // ---------------- +10: ดาวครบวง แล้วระเบิดแสง ----------------
        boolean complete(ClientLevel lvl, Vec3 right, boolean particles) {
            int[] pal = {c0, c1, c2, WHITE};
            if (t < T_COMPLETE) {
                double k = t / (double) T_COMPLETE;
                if (particles) {
                    if (t % 2 == 0) {
                        drawRing(lvl, right, 9, 4);
                        drawGhost(lvl, right, 9, c0, k);
                    }
                    double[] s = star(9);
                    Vec3 target = plane(right, s[0], s[1]);
                    gather(lvl, target, 1 + (int) (k * 4), 1.1, pal, 9);
                    // ดาวดวงอื่นส่งประกายเข้าไปช่วย
                    if (t % 4 == 0) {
                        int j = rnd.nextInt(9);
                        double[] q = star(j);
                        Vec3 from = plane(right, q[0], q[1]);
                        spark(lvl, from, target.subtract(from).scale(1.0 / 9.0), starColor(j), 1.2f, 9, 0, false);
                    }
                }
                if (screen) shake(0.3f + 1.4f * (float) k, 3);
            }
            if (t == T_COMPLETE) {
                if (particles) {
                    lvl.addParticle(ParticleTypes.FLASH, seal.x, seal.y, seal.z, 0, 0, 0);
                    lvl.addParticle(ParticleTypes.FLASH, top.x, top.y, top.z, 0, 0, 0);
                    for (int i = 0; i < 10; i++) {
                        double[] s = star(i);
                        burst(lvl, plane(right, s[0], s[1]), 10, 0.22, pal, 18, 1.2f, 0.2f);
                    }
                    for (int i = 0; i < 60; i++) {
                        double a = i / 60.0 * Math.PI * 2;
                        Vec3 v = right.scale(Math.cos(a) * 0.35).add(0, Math.sin(a) * 0.35, 0);
                        spark(lvl, seal, v, WHITE, 1.2f, 14, 0, true);
                    }
                    burst(lvl, seal, 110, 0.45, pal, 30, 1.3f, 0.3f);
                }
                if (screen) {
                    flash = 1f;
                    shake(4.5f, 14);
                }
            }
            if (particles && t >= T_COMPLETE) {
                int d = t - T_COMPLETE;
                // วงดาวครบ 10 ดวง เปลี่ยนสีวนตามชนิดไอเทม
                if (d < 44 && d % 3 == 0) {
                    for (int i = 0; i < 10; i++) drawStar(lvl, right, i, pal[(i + d / 3) % 3], 6);
                    emit(lvl, right, core, WHITE, c1, 6);
                }
                // คลื่นกระแทกตามพื้น
                if (d < 14) {
                    double r = 0.4 + d * 0.3;
                    for (int i = 0; i < 48; i++) {
                        double a = i / 48.0 * Math.PI * 2;
                        spark(lvl, ground.add(Math.cos(a) * r, 0, Math.sin(a) * r), Vec3.ZERO, pal[i % 3], 1.6f, 4, 0, true);
                    }
                }
                // เสาแสง
                if (d < 10) {
                    for (int i = 0; i < 12; i++) {
                        Vec3 p = top.add(rnd.nextDouble() * 0.3 - 0.15, 0, rnd.nextDouble() * 0.3 - 0.15);
                        spark(lvl, p, new Vec3(0, 0.6 + rnd.nextDouble() * 0.3, 0), pal[rnd.nextInt(4)], 1.2f, 20, 0, true);
                    }
                }
                // ละอองลอยค้าง
                if (d >= 12 && d < 64 && d % 2 == 0) {
                    for (int i = 0; i < 3; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * 3;
                        Vec3 p = ground.add(Math.cos(a) * r, rnd.nextDouble() * 3, Math.sin(a) * r);
                        spark(lvl, p, new Vec3(0, 0.02 + rnd.nextDouble() * 0.02, 0), pal[rnd.nextInt(4)], 0.9f, 30, 0, true);
                    }
                }
            }
            return t < T_COMPLETE + 70;
        }

        // ---------------- พลาด: ประกายฟุ้ง ดาวดวงล่าสุดดับ ----------------
        boolean fail(ClientLevel lvl, Vec3 right, boolean particles) {
            int prev = Math.max(0, Math.min(9, level)); // จำนวนดาวก่อนตี
            int target = prev;                           // ช่องที่พยายามจุด
            int dim = prev - 1;                          // ดาวที่จะดับ (-1 = ไม่มี)
            if (particles) {
                if (t < 32 && t % 3 == 0) {
                    drawRing(lvl, right, Math.max(0, dim), 6);
                    if (dim >= 0) {
                        double f = t < T_FIZZLE ? 0 : Math.min(1, (t - T_FIZZLE) / 12.0);
                        if (f < 1) drawStar(lvl, right, dim, lerp(starColor(dim), GRAY, f), 6);
                    }
                }
                double[] s = star(target);
                Vec3 at = plane(right, s[0], s[1]);
                if (t < T_FIZZLE) gather(lvl, at, 2, 0.7, new int[]{starColor(target), WHITE}, 8);
                if (t == T_FIZZLE) {
                    burst(lvl, at, 16, 0.12, new int[]{0x8A8AA0, 0x5A5A6A}, 14, 1.0f, 0.6f);
                    if (dim >= 0) {
                        double[] q = star(dim);
                        Vec3 dp = plane(right, q[0], q[1]);
                        for (int i = 0; i < 5; i++) {
                            lvl.addParticle(ParticleTypes.SMOKE, dp.x, dp.y, dp.z,
                                    (rnd.nextDouble() - 0.5) * 0.02, 0.03, (rnd.nextDouble() - 0.5) * 0.02);
                        }
                    }
                }
            }
            if (t == T_FIZZLE && screen) shake(0.8f, 8);
            return t < 34;
        }

        // ---------------- helpers ----------------

        /** วงดาว: แกนกลาง + ดาวที่สว่างแล้ว n ดวงแรก + จุดเทาของช่องที่ยังว่าง */
        void drawRing(ClientLevel lvl, Vec3 right, int litCount, int life) {
            emit(lvl, right, core, WHITE, BLUE, life);
            for (int i = 0; i < 10; i++) {
                if (i < litCount) drawStar(lvl, right, i, starColor(i), life);
                else {
                    double[] s = star(i);
                    spark(lvl, plane(right, s[0], s[1]), Vec3.ZERO, GRAY, 0.7f, life, 0, true);
                }
            }
        }

        /** ดาว 1 ดวง (จุดกลางขาว แขนสีของขั้น เส้นเชื่อมสีฟ้าอ่อน) */
        void drawStar(ClientLevel lvl, Vec3 right, int i, int color, int life) {
            List<double[]> pts = pieces[i];
            for (int k = 0; k < pts.size(); k++) {
                double[] p = pts.get(k);
                int c = k == 0 ? WHITE : (k <= 4 ? color : (k % 2 == 0 ? BLUE : PALE));
                spark(lvl, plane(right, p[0], p[1]), Vec3.ZERO, c, (float) p[2], life, 0, true);
            }
        }

        /** ดาวที่กำลังก่อตัว: โตขึ้นตาม progress 0..1 */
        void drawGhost(ClientLevel lvl, Vec3 right, int i, int color, double progress) {
            double[] s = star(i);
            float sc = (float) (0.4 + progress * 1.4);
            double jitter = 0.02 * progress;
            Vec3 p = plane(right, s[0] + (rnd.nextDouble() * 2 - 1) * jitter, s[1] + (rnd.nextDouble() * 2 - 1) * jitter);
            spark(lvl, p, Vec3.ZERO, progress > 0.7 ? WHITE : color, sc, 4, 0, true);
        }

        /** ประกายไหลเข้าหาจุดเดียว */
        void gather(ClientLevel lvl, Vec3 at, int n, double radius, int[] colors, int life) {
            for (int i = 0; i < n; i++) {
                Vec3 dir = new Vec3(rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1);
                if (dir.lengthSqr() < 1e-4) dir = new Vec3(1, 0, 0);
                Vec3 from = at.add(dir.normalize().scale(radius * (0.6 + rnd.nextDouble() * 0.4)));
                spark(lvl, from, at.subtract(from).scale(1.0 / life), colors[rnd.nextInt(colors.length)], 0.9f, life, 0, false);
            }
        }

        static int lerp(int a, int b, double f) {
            f = Math.max(0, Math.min(1, f));
            int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
            int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
            int bl = (int) Math.round((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
            return (r << 16) | (g << 8) | bl;
        }

        /** แกนแนวนอนของระนาบวงดาว ให้หันหน้าเข้าหากล้อง */
        Vec3 rightVector(Minecraft mc) {
            Camera cam = mc.gameRenderer.getMainCamera();
            Vec3 d = seal.subtract(cam.getPosition());
            double len = Math.sqrt(d.x * d.x + d.z * d.z);
            if (len < 0.1) {
                double yaw = Math.toRadians(cam.getYRot());
                return new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
            }
            return new Vec3(-d.z / len, 0, d.x / len);
        }

        Vec3 plane(Vec3 right, double u, double v) {
            return seal.add(right.scale(u)).add(0, v, 0);
        }

        void emit(ClientLevel lvl, Vec3 right, List<double[]> pts, int ca, int cb, int life) {
            for (int i = 0; i < pts.size(); i++) {
                double[] p = pts.get(i);
                spark(lvl, plane(right, p[0], p[1]), Vec3.ZERO, i % 2 == 0 ? ca : cb, (float) p[2], life, 0, true);
            }
        }

        void burst(ClientLevel lvl, Vec3 at, int n, double speed, int[] colors, int life, float scale, float gravity) {
            for (int i = 0; i < n; i++) {
                Vec3 dir = new Vec3(rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1);
                if (dir.lengthSqr() < 1e-4) dir = new Vec3(0, 1, 0);
                Vec3 v = dir.normalize().scale(speed * (0.35 + rnd.nextDouble() * 0.65));
                spark(lvl, at, v, colors[rnd.nextInt(colors.length)], scale, life + rnd.nextInt(Math.max(1, life / 3)), gravity, true);
            }
        }

        static void spark(ClientLevel lvl, Vec3 p, Vec3 v, int color, float scale, int life, float gravity, boolean fade) {
            lvl.addParticle(new SparkOptions(color, scale, life, gravity, fade), p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }
}
