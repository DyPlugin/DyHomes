package dev.dyhomes.rank;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RankRulesTest {
    private final YamlConfiguration config = new YamlConfiguration();
    private final RankRules ranks = new RankRules(() -> config);

    private Player player(String... nodes) {
        Player player = mock(Player.class);
        Set<PermissionAttachmentInfo> infos = java.util.Arrays.stream(nodes).map(node -> {
            PermissionAttachmentInfo info = mock(PermissionAttachmentInfo.class);
            when(info.getPermission()).thenReturn(node.startsWith("-") ? node.substring(1) : node);
            when(info.getValue()).thenReturn(!node.startsWith("-"));
            return info;
        }).collect(Collectors.toSet());
        when(player.getEffectivePermissions()).thenReturn(infos);
        when(player.hasPermission("dyhomes.limit.unlimited")).thenReturn(java.util.List.of(nodes).contains("dyhomes.limit.unlimited"));
        return player;
    }

    @Test void theHighestLimitWinsAndUnlimitedRemovesIt() {
        config.set("homes.default-limit", 3);
        assertEquals(3, ranks.homeLimit(player()));
        assertEquals(10, ranks.homeLimit(player("dyhomes.limit.5", "dyhomes.limit.10", "dyhomes.limit.abc")));
        assertEquals(1, ranks.homeLimit(player("dyhomes.limit.1")), "a rank may also lower the limit");
        assertEquals(3, ranks.homeLimit(player("-dyhomes.limit.20")), "a denied node does not count");
        assertEquals(RankRules.UNLIMITED, ranks.homeLimit(player("dyhomes.limit.unlimited")));
    }

    @Test void theLowestCooldownWinsUnlessOverridesAreOff() {
        assertEquals(60, ranks.cooldownSeconds(player(), 60));
        assertEquals(5, ranks.cooldownSeconds(player("dyhomes.cooldown.30", "dyhomes.cooldown.5"), 60));
        config.set("cooldown.permission-overrides", false);
        assertEquals(60, ranks.cooldownSeconds(player("dyhomes.cooldown.5"), 60));
    }
}
