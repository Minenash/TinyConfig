package net.fabricmc.example;


import net.fabricmc.example.config.TinyConfig;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.List;

@TinyConfig.TinyConfigInfo(modid = "example")
public class Config extends TinyConfig {

    @Entry(minimum = 5)
    public static int intTest = 20;

    @Entry(tooltip = "dieStrToolTip", maximum = 5)
    public static String dieStr = "lolz";

    @Entry()
    public static Test enumTest = Test.Test;



    private static final List<Text> dieStrToolTip = Collections.singletonList(new LiteralText("rrw"));
    public enum Test {
        Test, ER
    }

}
