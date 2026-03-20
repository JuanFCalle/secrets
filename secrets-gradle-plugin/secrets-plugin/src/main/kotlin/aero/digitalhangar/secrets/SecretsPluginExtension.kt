package aero.digitalhangar.secrets

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

abstract class SecretsPluginExtension {
    abstract val defaultSecretsFileName: Property<String>
    abstract val variantSecretsMapping: MapProperty<String, String>
    abstract val ignoreList: ListProperty<String>
}
