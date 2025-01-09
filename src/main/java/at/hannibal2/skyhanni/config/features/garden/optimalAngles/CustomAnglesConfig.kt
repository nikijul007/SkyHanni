package at.hannibal2.skyhanni.config.features.garden.optimalAngles;

import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.observer.Property;


public class CustomAnglesConfig {

    //Cactus
    @Expose
    @ConfigOption(name = "Cactus Yaw", desc = "Set Yaw for cactus farming.")
    @ConfigEditorSlider(minValue = -180, maxValue = 180, minStep = 0.1f)
    public Property<Float> cactusYaw = Property.of(-90f);

    @Expose
    @ConfigOption(name = "Cactus Pitch", desc = "Set Pitch for cactus farming.")
    @ConfigEditorSlider(minValue = -90, maxValue = 90, minStep = 0.1f)
    public Property<Float> cactusPitch = Property.of(0f);

    //Carrot
    @Expose
    @ConfigOption(name = "Carrot Yaw", desc = "Set Yaw for carrot farming.")
    @ConfigEditorSlider(minValue = -180, maxValue = 180, minStep = 0.1f)
    public Property<Float> carrotYaw = Property.of(-90f);

    @Expose
    @ConfigOption(name = "Carrot Pitch", desc = "Set Pitch for carrot farming.")
    @ConfigEditorSlider(minValue = -90, maxValue = 90, minStep = 0.1f)
    public Property<Float> carrotPitch = Property.of(2.8f);

    //Cocoa Beans
    @Expose
    @ConfigOption(name = "Cocoa Beans Yaw", desc = "Set Yaw for cocoa bean farming.")
    @ConfigEditorSlider(minValue = -180, maxValue = 180, minStep = 0.1f)
    public Property<Float> cocoaBeansYaw = Property.of(180f);

    @Expose
    @ConfigOption(name = "Cocoa Beans Pitch", desc = "Set Pitch for cocoa bean farming.")
    @ConfigEditorSlider(minValue = -90, maxValue = 90, minStep = 0.1f)
    public Property<Float> cocoaBeansPitch = Property.of(-45f);

    //Melon
    @Expose
    @ConfigOption(name = "Melon Yaw", desc = "Set Yaw for melon farming.")
    @ConfigEditorSlider(minValue = -180, maxValue = 180, minStep = 0.1f)
    public Property<Float> melonYaw = Property.of(90f);

    @Expose
    @ConfigOption(name = "Melon Pitch", desc = "Set Pitch for melon farming.")
    @ConfigEditorSlider(minValue = -90, maxValue = 90, minStep = 0.1f)
    public Property<Float> melonPitch = Property.of(-58.5f);

    //Mushroom
    @Expose
    @ConfigOption(name = "Mushroom Yaw", desc = "Set Yaw for mushroom farming.")
    @ConfigEditorSlider(minValue = -180, maxValue = 180, minStep = 0.1f)
    public Property<Float> mushroomYaw = Property.of(116.5f);

    @Expose
    @ConfigOption(name = "Mushroom Pitch", desc = "Set Pitch for mushroom farming.")
    @ConfigEditorSlider(minValue = -90, maxValue = 90, minStep = 0.1f)
    public Property<Float> mushroomPitch = Property.of(0f);

    //Nether Wart
    @Expose
    @ConfigOption(name = "Nether Wart Yaw", desc = "Set Yaw for nether wart farming.")
    @ConfigEditorSlider(minValue = -180, maxValue = 180, minStep = 0.1f)
    public Property<Float> netherWartYaw = Property.of(90f);

    @Expose
    @ConfigOption(name = "Nether Wart Pitch", desc = "Set Pitch for nether wart farming.")
    @ConfigEditorSlider(minValue = -90, maxValue = 90, minStep = 0.1f)
    public Property<Float> netherWartPitch = Property.of(0f);

    //Potato
    @Expose
    @ConfigOption(name = "Potato Yaw", desc = "Set Yaw for potato farming.")
    @ConfigEditorSlider(minValue = -180, maxValue = 180, minStep = 0.1f)
    public Property<Float> potatoYaw = Property.of(-90f);

    @Expose
    @ConfigOption(name = "Potato Pitch", desc = "Set Pitch for potato farming.")
    @ConfigEditorSlider(minValue = -90, maxValue = 90, minStep = 0.1f)
    public Property<Float> potatoPitch = Property.of(2.8f);

    //Pumpkin
    @Expose
    @ConfigOption(name = "Pumpkin Yaw", desc = "Set Yaw for pumpkin farming.")
    @ConfigEditorSlider(minValue = -180, maxValue = 180, minStep = 0.1f)
    public Property<Float> pumpkinYaw = Property.of(90f);

    @Expose
    @ConfigOption(name = "Pumpkin Pitch", desc = "Set Pitch for pumpkin farming.")
    @ConfigEditorSlider(minValue = -90, maxValue = 90, minStep = 0.1f)
    public Property<Float> pumpkinPitch = Property.of(-58.5f);

    //Sugar Cane
    @Expose
    @ConfigOption(name = "Sugar Cane Yaw", desc = "Set Yaw for sugar cane farming.")
    @ConfigEditorSlider(minValue = -180, maxValue = 180, minStep = 0.1f)
    public Property<Float> sugarCaneYaw = Property.of(-135f);

    @Expose
    @ConfigOption(name = "Sugar Cane Pitch", desc = "Set Pitch for sugar cane farming.")
    @ConfigEditorSlider(minValue = -90, maxValue = 90, minStep = 0.1f)
    public Property<Float> sugarCanePitch = Property.of(0f);

    //Wheat
    @Expose
    @ConfigOption(name = "Wheat Yaw", desc = "Set Yaw for wheat farming.")
    @ConfigEditorSlider(minValue = -180, maxValue = 180, minStep = 0.1f)
    public Property<Float> wheatYaw = Property.of(90f);

    @Expose
    @ConfigOption(name = "Wheat Pitch", desc = "Set Pitch for wheat farming.")
    @ConfigEditorSlider(minValue = -90, maxValue = 90, minStep = 0.1f)
    public Property<Float> wheatPitch = Property.of(0f);

}
