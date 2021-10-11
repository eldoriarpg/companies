package de.eldoria.companies.serialization;

import de.eldoria.companies.util.SerializeContainer;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SerializeContainerTest {

  @Test
  public void serializeItemStack() {
    ConfigurationSerialization.registerClass(TestPojo.class, "TestPojo");

    var container = SerializeContainer.fromObject(new TestPojo(33, Material.ACACIA_BOAT));
    var json = container.toJson();

    var deserializeObject = SerializeContainer.deserializeFromJson(json, TestPojo.class);
    Assertions.assertEquals(deserializeObject.getMaterial(), Material.ACACIA_BOAT);
    Assertions.assertEquals(deserializeObject.getAmount(), 33);
  }

  @SerializableAs("TestPojo")
  public static class TestPojo implements ConfigurationSerializable {

    private final int amount;
    private final Material material;

    private TestPojo(int amount, Material material) {
      this.amount = amount;
      this.material = material;
    }

    public static TestPojo deserialize(Map<String, Object> map) {
      return new TestPojo(Double.valueOf(map.get("amount").toString()).intValue(),
          Material.valueOf((String) map.get("material")));
    }

    @NotNull
    @Override
    public Map<String, Object> serialize() {
      return Map.ofEntries(Map.entry("material", material), Map.entry("amount", amount));
    }

    public Material getMaterial() {
      return material;
    }

    public int getAmount() {
      return amount;
    }
  }

}
