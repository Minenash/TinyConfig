package net.fabricmc.example;


import net.fabricmc.example.config.TinyConfig;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.List;

public class ExampleConfig extends TinyConfig {

    @Entry
    public static boolean boolTest = false;

    @Entry(min = 5)
    public static int intTest = 20;


    @Entry
    public static double decimalTest = 20;

    @Entry(dynamicTooltip = "dieStrToolTip", max = 5)
    public static String dieStr = "lolz";

    @Entry
    public static Test enumTest = Test.Test;


    private static List<Text> dieStrTooltip(List<EntryInfo> infos) {
        return Collections.singletonList(new LiteralText("safbjkasf"));
    }

    public enum Test {
        Test, ER
    }

}
