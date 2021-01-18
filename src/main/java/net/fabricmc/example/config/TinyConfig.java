package net.fabricmc.example.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.example.Config;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

import java.io.IOException;
import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class TinyConfig {

    private static final Pattern INTEGER_ONLY = Pattern.compile("(-[0-9]+|[0-9]*)");
    private static final Pattern DECIMAL_ONLY = Pattern.compile("-?([\\d]+\\.?[\\d]*|[\\d]*\\.?[\\d]+|)");

    private static final Map<String, Map.Entry<Object,Integer>> entries = new LinkedHashMap<>();
    private static final Map<String, Object> tooltips = new LinkedHashMap<>();
    private static final Map<String, Map.Entry<TextFieldWidget,String>> errors = new HashMap<>();
    private static final Map<String, Field> fields = new HashMap<>();
    private static final Map<String, Object> options = new HashMap<>();

    private static String translationPrefix;
    private static Path path;

    private static final Gson gson = new GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.TRANSIENT)
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .setPrettyPrinting()
            .create();

    public static void init(Class<?> config) {
        try {
            TinyConfigInfo info = config.getAnnotation(TinyConfigInfo.class);
            translationPrefix = info.modid() + ".tinyconfig.";
            path = FabricLoader.getInstance().getConfigDir().resolve(info.modid() + ".json");
        }
        catch (Exception ignored) {
            translationPrefix = config.getName() + ".tinyconfig.";
            path = FabricLoader.getInstance().getConfigDir().resolve(config.getName() + ".json");
        }

        try {
            gson.fromJson(Files.newBufferedReader(path), config);
        }
        catch (Exception e) {
            write();
        }

        for (Field field : config.getFields()) {
            String name = field.getName(), tooltip;
            double min, max;
            int width;
            try {
                Entry entry = field.getAnnotation(Entry.class);
                min = entry.minimum();
                max = entry.maximum();
                width = entry.width();
                tooltip = entry.tooltip();

            } catch (Exception ignored) { continue; }

            Class<?> type = field.getType();

            if (type == int.class)         textField(name, Integer::parseInt, INTEGER_ONLY, min, max, width, true);
            else if (type == double.class) textField(name, Double::parseDouble, DECIMAL_ONLY, min, max, width,false);
            else if (type == String.class) textField(name, String::length, null, Math.min(min,0), Math.max(max,1), width,true);
            else if (type == Boolean.class) {
                entries.put(name, new AbstractMap.SimpleEntry<>((ButtonWidget.PressAction) button -> {
                    boolean bool = !(Boolean) options.get(name);
                    options.put(name, bool);
                    button.setMessage(new LiteralText(String.valueOf(bool)));
                }, width));
            }
            else if (type.isEnum()) {
                List<?> values = Arrays.asList(field.getType().getEnumConstants());
                entries.put(name, new AbstractMap.SimpleEntry<>((ButtonWidget.PressAction) button -> {
                    Object ef = options.get(name);
                    int index = values.indexOf(ef) + 1;
                    ef = values.get(index >= values.size()? 0 : index);

                    options.put(name, ef);
                    button.setMessage(new TranslatableText(translationPrefix + "." + field.getType().getName() + "." + ef.toString()));
                }, width));
            }
            else
                continue;

            try {
                options.put(name, field.get(null));
                fields.put(name, field);
            } catch (IllegalAccessException ignored) {}

            try {
                Field f = config.getDeclaredField(tooltip);
                f.setAccessible(true);
                List<Text> list = (List<Text>) f.get(null);
                if (list.size() > 0)
                    tooltips.put(name, list);
            } catch (Exception e) {
                tooltips.put(name, translationPrefix + name + ".tooltip");
            }

        }

    }

    private static void textField(String title, Function<String,Number> f, Pattern pattern, double min, double max, int width, boolean cast) {
        entries.put(title, new AbstractMap.SimpleEntry<>((BiFunction<TextFieldWidget, ButtonWidget, Predicate<String>>) (t, b) -> s -> {
            boolean valid = s.isEmpty() || pattern == null || pattern.matcher(s).matches();
            Number value = s.isEmpty() ? 0 : f.apply(s);

            if (valid && value.doubleValue() >= min && value.doubleValue() <= max) {
                options.put(title, value);
                t.setEditableColor(0xFFFFFFFF);
                b.active = true;
                errors.remove(title);
            } else {
                t.setEditableColor(0xFFFF7777);
                b.active = false;
                errors.put(title, new AbstractMap.SimpleEntry<>(t, value.doubleValue() < min ?
                        "§cMinimum " + (pattern == null? "length" : "value") + (cast? " is " + (int)min : " is " + min)  :
                        "§cMaximum " + (pattern == null? "length" : "value") + (cast? " is " + (int)max : " is " + max)));
            }
            return valid;
        }, width));
    }

    public static void write() {
        try {
            if (!Files.exists(path)) Files.createFile(path);
            Files.write(path, gson.toJson(new Config()).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public Screen getScreen(Screen parent) {
        return new TinyConfigScreen(parent);
    }

    private static class TinyConfigScreen extends Screen {
        protected TinyConfigScreen(Screen parent) {
            super(new TranslatableText(TinyConfig.translationPrefix + "title"));
            this.parent = parent;
        }

        private final List<TextFieldWidget> textFieldWidgets = new ArrayList<>();
        private final Screen parent;

        @Override
        protected void init() {
            super.init();
            textFieldWidgets.clear();

            ButtonWidget done = this.addButton(new ButtonWidget(this.width/2 - 100,this.height - 28,200,20,
                    new TranslatableText("gui.done"), (button) -> {
                for (Map.Entry<String, Field> entry : fields.entrySet())
                    try {
                        entry.getValue().set(null, options.get(entry.getKey()));
                    } catch (IllegalAccessException ignore) {}
                write();
                client.openScreen(parent);
            }));

            int y = 45;
            for (Map.Entry<String,Map.Entry<Object, Integer>> entry : entries.entrySet()) {
                String text = String.valueOf(options.get(entry.getKey()));
                if (entry.getValue().getKey() instanceof ButtonWidget.PressAction) {
                    addButton(new ButtonWidget(width-85,y,entry.getValue().getValue(),20, new LiteralText(text), (ButtonWidget.PressAction) entry.getValue().getKey()));
                }
                else {
                    TextFieldWidget widget = new TextFieldWidget(textRenderer, width-85, y, entry.getValue().getValue(), 20, null);
                    widget.setText(text);
                    widget.setTextPredicate(((BiFunction<TextFieldWidget,ButtonWidget,Predicate<String>>) entry.getValue().getKey()).apply(widget,done));
                    children.add(widget);
                    textFieldWidgets.add(widget);
                }
                y += 30;
            }

        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            this.renderBackground(matrices);

            if (mouseY >= 40 && mouseY <= 39 + entries.size()*30) {
                int low = ((mouseY-10)/30)*30 + 10 + 2;
                fill(matrices, 0, low, width, low+30-4, 0x33FFFFFF);
            }

            drawCenteredText(matrices, textRenderer, title, width/2, 10, 0xFFFFFF);

            int y = 20;
            for (String entry : entries.keySet())
                drawTextWithShadow(matrices, textRenderer, new TranslatableText(translationPrefix + entry), 10, y += 30, 0xFFFFFF);

            for (TextFieldWidget widget : textFieldWidgets)
                widget.render(matrices, mouseX, mouseY, delta);

            y = 40;
            for (String name : entries.keySet()) {
                Object tooltip = tooltips.get(name);
                if (tooltip != null) {
                    Map.Entry<TextFieldWidget,String> error = errors.get(name);
                    if (error != null && error.getKey().isMouseOver(mouseX,mouseY))
                        renderTooltip(matrices, new LiteralText(error.getValue()), mouseX, mouseY);
                    else if (mouseY >= y && mouseY <= (y + 30)) {
                        if (tooltip instanceof List)
                            renderTooltip(matrices, (List<Text>) tooltip, mouseX, mouseY);
                        else if (I18n.hasTranslation(tooltip.toString())) {
                            List<Text> list = new ArrayList<>();
                            for (String str : I18n.translate(tooltip.toString()).split("\n"))
                                list.add(new LiteralText(str));
                            renderTooltip(matrices, list, mouseX, mouseY);
                        }
                    }
                }
                y += 30;
            }

            super.render(matrices, mouseX, mouseY, delta);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface TinyConfigInfo {
        String modid();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Entry {
        String tooltip() default "";
        int width() default 75;
        double minimum() default Double.MIN_NORMAL;
        double maximum() default Double.MAX_VALUE;
    }

}
