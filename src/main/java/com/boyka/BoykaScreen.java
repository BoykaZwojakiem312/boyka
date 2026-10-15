package com.boyka;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;

public class BoykaScreen extends Screen {
    private static final int PW = 400, PH = 236;
    private static final int NONE = -2, MINE = -1;
    private static final int ROW = 18;
    private static final String[] TABS = {"Auto MineTask", "Leave", "Friends"};

    // kolory (niebieski, nie za ciemny, nie za jasny)
    private static final int C_BORDER = 0xFF16294A;
    private static final int C_PANEL = 0xFF2E5A94;
    private static final int C_HEADER = 0xFF1F4175;
    private static final int C_TAB = 0xFF3A6CB0;
    private static final int C_TAB_HOVER = 0xFF4A80CC;
    private static final int C_TAB_ON = 0xFF5B9BEF;
    private static final int C_SLOT = 0xFF24426E;
    private static final int C_GREEN = 0xFF22C55E;
    private static final int C_TEXT = 0xFFE6F0FF;

    private static int tab = 0;

    private int px, py, rx;
    private int waitingFor = NONE;
    private TextFieldWidget friendField;
    private String friendMsg = "";
    private int scroll = 0;
    private ItemStack hovered = ItemStack.EMPTY;

    public BoykaScreen() {
        super(Text.literal("BOYKA"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // celowo puste - tło rysujemy sami w render()
    }

    @Override
    public void removed() {
        Config.save();
    }

    // ------------------------------------------------------------------ INIT

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        rx = px + 200;
        if (tab == 0) initMine();
        else if (tab == 1) initLeave();
        else initFriends();
    }

    private void initMine() {
        Config c = Config.get();
        addDrawableChild(new IntSlider(px + 14, py + 170, 162, 18, "Kratki", 0, 150, c.blocks, v -> c.blocks = v));
        addDrawableChild(keyButton(px + 80, py + 196, 96, 18, c.mineKey, MINE, "[ brak ]"));
        addDrawableChild(ButtonWidget.builder(Text.literal(AutoMiner.active ? "STOP" : "START"), b -> {
            AutoMiner.toggle(client);
            close();
        }).dimensions(rx, py + 196, 186, 18).build());
    }

    private void initLeave() {
        Config c = Config.get();
        addDrawableChild(new IntSlider(px + 100, py + 118, 200, 18, "Zasięg (bloki)", 1, 50, c.leaveRadius,
                v -> c.leaveRadius = v));
    }

    private void initFriends() {
        friendField = new TextFieldWidget(textRenderer, px + 14, py + 62, 280, 18, Text.empty());
        friendField.setMaxLength(20);
        friendField.setPlaceholder(Text.literal("Nick gracza"));
        addDrawableChild(friendField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Dodaj"), b -> addFriend())
                .dimensions(px + 300, py + 62, 86, 18).build());
    }

    private ButtonWidget keyButton(int x, int y, int w, int h, int key, int id, String none) {
        String label = waitingFor == id ? "> naciśnij klawisz <" : BoykaMod.keyName(key, none);
        return ButtonWidget.builder(Text.literal(label), b -> {
            waitingFor = id;
            clearAndInit();
        }).dimensions(x, y, w, h).build();
    }

    private void addFriend() {
        Config c = Config.get();
        String n = friendField.getText().trim();
        if (n.isEmpty()) return;
        if (!n.matches("[A-Za-z0-9_.]{1,20}")) {
            friendMsg = "Niepoprawny nick.";
        } else if (c.friends.size() >= 50) {
            friendMsg = "Limit 50 graczy.";
        } else if (c.isFriend(n)) {
            friendMsg = "Ten gracz już jest na liście.";
        } else {
            c.friends.add(n);
            friendField.setText("");
            friendMsg = "Dodano: " + n;
            Config.save();
        }
    }

    // ---------------------------------------------------------------- RENDER

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        hovered = ItemStack.EMPTY;
        ctx.fill(0, 0, width, height, 0x77000000);
        ctx.fill(px - 2, py - 2, px + PW + 2, py + PH + 2, C_BORDER);
        ctx.fill(px, py, px + PW, py + PH, C_PANEL);
        ctx.fill(px, py, px + PW, py + 32, C_HEADER);

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(px + PW / 2f, py + 9, 0);
        m.scale(2f, 2f, 1f);
        ctx.drawCenteredTextWithShadow(textRenderer, "BOYKA", 0, 0, 0xFFFFFFFF);
        m.pop();

        for (int i = 0; i < TABS.length; i++) {
            int x = px + 12 + i * 128, y = py + 36;
            boolean over = in(mx, my, x, y, 120, 18);
            ctx.fill(x, y, x + 120, y + 18, i == tab ? C_TAB_ON : (over ? C_TAB_HOVER : C_TAB));
            ctx.drawBorder(x, y, 120, 18, C_BORDER);
            ctx.drawCenteredTextWithShadow(textRenderer, TABS[i], x + 60, y + 5, 0xFFFFFFFF);
        }

        if (tab == 0) renderMine(ctx, mx, my);
        else if (tab == 1) renderLeave(ctx);
        else renderFriends(ctx, mx, my);

        super.render(ctx, mx, my, delta);

        if (!hovered.isEmpty()) ctx.drawItemTooltip(textRenderer, hovered, mx, my);
    }

    private void renderMine(DrawContext ctx, int mx, int my) {
        Config c = Config.get();
        ctx.drawText(textRenderer, "Ekwipunek - kliknij narzędzie", px + 14, py + 58, C_TEXT, false);
        ctx.drawText(textRenderer, "Auto Repair", rx, py + 58, C_TEXT, false);

        for (int idx = 0; idx < 36; idx++) {
            int[] pos = slotPos(idx);
            int x = pos[0], y = pos[1];
            boolean sel = idx == c.toolSlot;
            ctx.fill(x, y, x + 18, y + 18, sel ? 0xFF1FA84F : C_SLOT);
            ctx.drawBorder(x, y, 18, 18, sel ? 0xFF8CFFAE : C_BORDER);
            ItemStack st = client.player.getInventory().getStack(idx);
            if (!st.isEmpty()) {
                ctx.drawItem(st, x + 1, y + 1);
                ctx.drawStackOverlay(textRenderer, st, x + 1, y + 1);
                if (in(mx, my, x, y, 18, 18)) hovered = st;
            }
        }

        checkbox(ctx, px + 14, py + 152, c.right, "line.R");
        checkbox(ctx, px + 84, py + 152, !c.right, "line.L");
        ctx.drawText(textRenderer, "key bind -", px + 14, py + 201, C_TEXT, false);

        checkbox(ctx, rx, py + 78, c.repairOn, "/repair");
        checkbox(ctx, rx, py + 98, c.repairAllOn, "/repair all");
        ctx.drawText(textRenderer, "Wysyłaj co:", rx, py + 122, C_TEXT, false);
        for (int i = 0; i < 5; i++) {
            int x = rx + i * 38, y = py + 136;
            boolean sel = c.repairMinutes == i + 1;
            boolean over = in(mx, my, x, y, 34, 18);
            ctx.fill(x, y, x + 34, y + 18, sel ? 0xFF1FA84F : (over ? C_TAB_HOVER : C_TAB));
            ctx.drawBorder(x, y, 34, 18, sel ? 0xFF8CFFAE : C_BORDER);
            ctx.drawCenteredTextWithShadow(textRenderer, (i + 1) + " min", x + 17, y + 5, 0xFFFFFFFF);
        }
        ctx.drawText(textRenderer, "Działa tylko podczas kopania.", rx, py + 164, 0xFFB8D0F0, false);
    }

    private void renderLeave(DrawContext ctx) {
        Config c = Config.get();
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(px + PW / 2f, py + 62, 0);
        m.scale(1.6f, 1.6f, 1f);
        ctx.drawCenteredTextWithShadow(textRenderer, "Auto Leave", 0, 0, 0xFFFFFFFF);
        m.pop();

        checkbox(ctx, px + 100, py + 92, c.leaveEnabled, "Auto Leave: " + (c.leaveEnabled ? "ON" : "OFF"));

        int cx = px + PW / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, "Gdy gracz spoza listy Friends wejdzie w ten zasięg,", cx, py + 152, C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, "automatycznie wyloguje Cię z serwera.", cx, py + 164, C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, "Nie wejdzie z powrotem, dopóki sam nie wejdziesz.", cx, py + 176, C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, "Znajomych w Friends: " + c.friends.size() + "/50", cx, py + 198, 0xFF9CFFB8);
    }

    private int listTop() { return py + 100; }
    private int listBottom() { return py + PH - 10; }

    private void renderFriends(DrawContext ctx, int mx, int my) {
        Config c = Config.get();
        ctx.drawText(textRenderer, "Friends: " + c.friends.size() + "/50", px + 14, py + 86, C_TEXT, false);
        if (!friendMsg.isEmpty()) ctx.drawText(textRenderer, friendMsg, px + 110, py + 86, 0xFFFFE08A, false);

        int top = listTop(), bottom = listBottom();
        ctx.fill(px + 14, top, px + 386, bottom, 0xFF21436F);
        ctx.enableScissor(px + 14, top, px + 386, bottom);
        for (int i = 0; i < c.friends.size(); i++) {
            int y = top + i * ROW - scroll;
            if (y + ROW < top || y > bottom) continue;
            ctx.fill(px + 14, y, px + 386, y + ROW - 1, i % 2 == 0 ? 0xFF2C5388 : 0xFF305A94);
            ctx.drawText(textRenderer, c.friends.get(i), px + 22, y + 5, 0xFFFFFFFF, false);
            int tx = px + 386 - 22, ty = y + 3;
            boolean over = in(mx, my, tx - 2, ty - 2, 16, 16) && my >= top && my < bottom;
            trash(ctx, tx, ty, over ? 0xFFFF7A7A : 0xFFE05555);
        }
        ctx.disableScissor();
        if (c.friends.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Pusto - dodaj nick powyżej", px + PW / 2, top + 10, 0xFFB8D0F0);
        }
    }

    private void trash(DrawContext ctx, int x, int y, int col) {
        ctx.fill(x + 3, y, x + 7, y + 1, col);          // uchwyt
        ctx.fill(x, y + 1, x + 10, y + 3, col);         // wieczko
        ctx.fill(x + 1, y + 4, x + 9, y + 11, col);     // korpus
        ctx.fill(x + 3, y + 5, x + 4, y + 10, 0xFF21436F);
        ctx.fill(x + 6, y + 5, x + 7, y + 10, 0xFF21436F);
    }

    private void checkbox(DrawContext ctx, int x, int y, boolean on, String label) {
        ctx.fill(x, y, x + 12, y + 12, 0xFF0F2038);
        ctx.fill(x + 1, y + 1, x + 11, y + 11, on ? C_GREEN : 0xFF2B4A7A);
        if (on) {
            int[][] px = {{3, 6}, {4, 7}, {5, 8}, {6, 7}, {7, 6}, {8, 5}, {9, 4}};
            for (int[] p : px) ctx.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 2, 0xFFFFFFFF);
        }
        ctx.drawText(textRenderer, label, x + 16, y + 2, C_TEXT, false);
    }

    private int checkboxWidth(String label) {
        return 16 + textRenderer.getWidth(label);
    }

    private int[] slotPos(int idx) {
        if (idx < 9) return new int[]{px + 14 + idx * 18, py + 70 + 54 + 6};
        int r = (idx - 9) / 9, col = (idx - 9) % 9;
        return new int[]{px + 14 + col * 18, py + 70 + r * 18};
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ----------------------------------------------------------------- INPUT

    @Override
    public boolean mouseClicked(double dmx, double dmy, int button) {
        int mx = (int) dmx, my = (int) dmy;
        Config c = Config.get();
        if (button == 0) {
            for (int i = 0; i < TABS.length; i++) {
                if (in(mx, my, px + 12 + i * 128, py + 36, 120, 18)) {
                    tab = i;
                    waitingFor = NONE;
                    scroll = 0;
                    clearAndInit();
                    return true;
                }
            }
            if (tab == 0) {
                for (int idx = 0; idx < 36; idx++) {
                    int[] p = slotPos(idx);
                    if (in(mx, my, p[0], p[1], 18, 18)) {
                        c.toolSlot = (c.toolSlot == idx) ? -1 : idx;
                        Config.save();
                        return true;
                    }
                }
                if (in(mx, my, px + 14, py + 152, checkboxWidth("line.R"), 12)) {
                    c.right = true;
                    Config.save();
                    return true;
                }
                if (in(mx, my, px + 84, py + 152, checkboxWidth("line.L"), 12)) {
                    c.right = false;
                    Config.save();
                    return true;
                }
                if (in(mx, my, rx, py + 78, checkboxWidth("/repair"), 12)) {
                    c.repairOn = !c.repairOn;
                    Config.save();
                    return true;
                }
                if (in(mx, my, rx, py + 98, checkboxWidth("/repair all"), 12)) {
                    c.repairAllOn = !c.repairAllOn;
                    Config.save();
                    return true;
                }
                for (int i = 0; i < 5; i++) {
                    if (in(mx, my, rx + i * 38, py + 136, 34, 18)) {
                        c.repairMinutes = i + 1;
                        Config.save();
                        return true;
                    }
                }
            } else if (tab == 1) {
                String label = "Auto Leave: " + (c.leaveEnabled ? "ON" : "OFF");
                if (in(mx, my, px + 100, py + 92, checkboxWidth(label), 12)) {
                    c.leaveEnabled = !c.leaveEnabled;
                    Config.save();
                    return true;
                }
            } else {
                int top = listTop(), bottom = listBottom();
                if (my >= top && my < bottom) {
                    for (int i = 0; i < c.friends.size(); i++) {
                        int y = top + i * ROW - scroll;
                        if (in(mx, my, px + 386 - 24, y + 1, 18, 16)) {
                            friendMsg = "Usunięto: " + c.friends.remove(i);
                            Config.save();
                            clampScroll();
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(dmx, dmy, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (tab == 2) {
            scroll -= (int) (vAmount * ROW);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    private void clampScroll() {
        int max = Math.max(0, Config.get().friends.size() * ROW - (listBottom() - listTop()));
        scroll = Math.max(0, Math.min(max, scroll));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Config c = Config.get();
        if (waitingFor != NONE) {
            int k = keyCode == GLFW.GLFW_KEY_ESCAPE ? -1 : keyCode;
            c.mineKey = k;
            waitingFor = NONE;
            Config.save();
            clearAndInit();
            return true;
        }
        if (tab == 2 && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && friendField != null && friendField.isFocused()) {
            addFriend();
            return true;
        }
        boolean typing = getFocused() instanceof TextFieldWidget tf && tf.isFocused();
        if (!typing && BoykaMod.openKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---------------------------------------------------------------- SLIDER

    private static class IntSlider extends SliderWidget {
        private final int min, max;
        private final String label;
        private final IntConsumer onChange;

        IntSlider(int x, int y, int w, int h, String label, int min, int max, int value, IntConsumer onChange) {
            super(x, y, w, h, Text.empty(), (value - min) / (double) (max - min));
            this.min = min;
            this.max = max;
            this.label = label;
            this.onChange = onChange;
            updateMessage();
        }

        private int get() {
            return min + (int) Math.round(value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + get()));
        }

        @Override
        protected void applyValue() {
            onChange.accept(get());
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            Config.save();
        }
    }
}
