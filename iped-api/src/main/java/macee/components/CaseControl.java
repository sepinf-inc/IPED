package macee.components;

import java.util.Collection;
import java.util.UUID;
import macee.descriptor.Descriptor;
import macee.descriptor.DescriptorType;

public interface CaseControl extends Searcher, FilterManager, ItemManager, CollectionManager, Reporter {

    UUID GUID_HIGHLIGHTED = UUID.fromString("ad934e69-b283-4fed-8a60-c457df92b184");
    UUID GUID_CHECKED = UUID.fromString("dd019ca3-bbea-4e7d-9f20-7acede7affaf");
    UUID GUID_IGNORED = UUID.fromString("0fb29df8-1583-4a47-8238-aeacd3a5d989");

    // DESCRITORES
    Collection<UUID> getDescriptors();

    Collection<UUID> getDescriptorsByType(DescriptorType type);

    Descriptor getDescriptor(String guid);

    Descriptor getDescriptor(String name, DescriptorType type);
}
