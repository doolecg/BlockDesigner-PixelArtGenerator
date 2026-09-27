package io.blockdesigner.pixelart;

import io.blockdesigner.plugin.Options;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The build settings as the Generate page and the Import window show them: grouped, with units and help. */
class BuildOptionsTest {
    private final Options o = BuildOptions.OPTIONS;

    @Test
    void everyOptionIsInOneGroupInOrder() {
        assertThat(o.groups()).extracting(Options.Group::title).containsExactly("Shape", "Blocks", "Background", "Picture adjustments");
        List<String> keys = new ArrayList<>();
        o.groups().forEach(g -> keys.addAll(g.keys()));
        assertThat(keys).containsExactlyElementsOf(o.all().stream().map(Options.Option::key).toList());
    }

    @Test
    void pictureAdjustmentsAreAdvanced() {
        assertThat(o.groups()).filteredOn(Options.Group::advanced).extracting(Options.Group::title).containsExactly("Picture adjustments");
        assertThat(o.groups().getLast().keys()).containsExactly("brightness", "contrast", "saturation");
    }

    @Test
    void unitsAndHelp() {
        assertThat(o.unit("width")).contains("blocks");
        assertThat(o.unit("smoothing")).contains("px");
        assertThat(o.unit("minRegion")).contains("px");
        assertThat(o.unit("tolerance")).contains("%");
        assertThat(o.help("maxBlocks")).hasValueSatisfying(h -> assertThat(h).contains("0"));
        assertThat(o.help("terraces")).isPresent();
        // Labels no longer carry their units or hints in brackets.
        o.all().forEach(x -> assertThat(x.label()).doesNotContain("("));
    }
}
