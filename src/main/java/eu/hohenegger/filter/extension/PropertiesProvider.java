package eu.hohenegger.filter.extension;

import java.util.List;

import javax.inject.Named;

@Named("PropertiesProvider")
public class PropertiesProvider {

  public static final String FILTER_PLUGINS_SYS_PROP = "filterPlugins";
  
  public boolean isConfigured() {
    return System.getProperties().containsKey(FILTER_PLUGINS_SYS_PROP);
  }

  public List<String> getPluginDescriptors() {
    return List.of(System.getProperty(FILTER_PLUGINS_SYS_PROP).split(","));
  }
  
}
