/*
 * The MIT License
 *
 * Copyright (c) 2011 Steven G. Brown
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.filesfoundtrigger;

import static hudson.Util.fixNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.slaves.NodeProperty;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.RobustReflectionConverter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.google.common.base.Objects;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.mapper.Mapper;

/**
 * Pattern of files to locate within a single directory.
 * 
 * @author Steven G. Brown
 */
public final class FilesFoundTriggerConfig implements
    Describable<FilesFoundTriggerConfig> {

  /**
   * Get the descriptor of this class.
   * 
   * @return the {@link FilesFoundTriggerConfig} descriptor
   */
  public static DescriptorImpl getClassDescriptor() {
    return (DescriptorImpl) Hudson.getInstance().getDescriptorOrDie(
        FilesFoundTriggerConfig.class);
  }

  private static final Logger LOGGER = Logger
      .getLogger(FilesFoundTriggerConfig.class.getName());

  private static String fixNode(String node) {
    if (node != null) {
      node = node.trim();
      if (node.isEmpty() || node.equalsIgnoreCase("master")) {
        node = null;
      }
    }
    return node;
  }

  /**
   * The slave node on which to look for files, or {@code null} if the master
   * will be used.
   */
  private final String node;

  /**
   * The base directory to use when locating files.
   */
  private final String directory;

  /**
   * The pattern of files to locate under the base directory.
   */
  private final String files;

  /**
   * The pattern of files to ignore when searching under the base directory.
   */
  private final String ignoredFiles;

  /**
   * Create a new {@link FilesFoundTriggerConfig}.
   * 
   * @param node
   *          the node on which to look for files (the master or a slave node)
   * @param directory
   *          the base directory to use when locating files
   * @param files
   *          the pattern of files to locate under the base directory
   * @param ignoredFiles
   *          the pattern of files to ignore when searching under the base
   *          directory
   */
  @DataBoundConstructor
  public FilesFoundTriggerConfig(String node, String directory, String files,
      String ignoredFiles) {
    this.node = fixNode(node);
    this.directory = fixNull(directory).trim();
    this.files = fixNull(files).trim();
    this.ignoredFiles = fixNull(ignoredFiles).trim();
  }

  /**
   * Constructor intended to be called by XStream only. Sets the default field
   * values, which will then be overridden if these fields exist in the build
   * configuration file.
   */
  @SuppressWarnings("unused")
  // called reflectively by XStream
  private FilesFoundTriggerConfig() {
    this.node = null;
    this.directory = "";
    this.files = "";
    this.ignoredFiles = "";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Descriptor<FilesFoundTriggerConfig> getDescriptor() {
    return getClassDescriptor();
  }

  /**
   * Get the slave node on which to look for files, or {@code null} if the
   * master will be used.
   * 
   * @return the node on which to look for files
   */
  public String getNode() {
    return node;
  }

  /**
   * Get the base directory to use when locating files.
   * 
   * @return the base directory
   */
  public String getDirectory() {
    return directory;
  }

  /**
   * Get the pattern of files to locate under the base directory.
   * 
   * @return the files to find
   */
  public String getFiles() {
    return files;
  }

  /**
   * Get the pattern of files to ignore when searching under the base directory.
   * 
   * @return the files to ignore
   */
  public String getIgnoredFiles() {
    return ignoredFiles;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return Objects.toStringHelper(this).add("node", node)
        .add("directory", directory).add("files", files)
        .add("ignoredFiles", ignoredFiles).toString();
  }

  /**
   * Expand environment variables and global properties in each field.
   * 
   * @return the expanded configuration
   */
  FilesFoundTriggerConfig expand() {
    EnvVars vars = new EnvVars();

    // Environment variables
    vars.overrideAll(System.getenv());

    // Global properties
    for (NodeProperty<?> property : Hudson.getInstance()
        .getGlobalNodeProperties()) {
      if (property instanceof EnvironmentVariablesNodeProperty) {
        vars.overrideAll(((EnvironmentVariablesNodeProperty) property)
            .getEnvVars());
      }
    }

    // Expand each field
    String expNode = node == null ? null : vars.expand(node);
    String expDirectory = vars.expand(directory);
    String expFiles = vars.expand(files);
    String expIgnoredFiles = vars.expand(ignoredFiles);

    return new FilesFoundTriggerConfig(expNode, expDirectory, expFiles,
        expIgnoredFiles);
  }

  /**
   * Search for the files.
   * 
   * @return the files found
   */
  List<String> findFiles() {
    try {
      return FileSearch.perform(this).files;
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.log(Level.WARNING, e.getMessage(), e);
    }
    return Collections.emptyList();
  }

  /**
   * {@link Converter} implementation for XStream. This converter uses the
   * {@link PureJavaReflectionProvider}, which ensures that the default
   * constructor is called.
   */
  public static final class ConverterImpl extends RobustReflectionConverter {

    /**
     * Class constructor.
     * 
     * @param mapper
     *          the mapper
     */
    public ConverterImpl(Mapper mapper) {
      super(mapper, new PureJavaReflectionProvider());
    }
  }

  /**
   * Descriptor for the {@link FilesFoundTriggerConfig} class.
   */
  @Extension
  public static final class DescriptorImpl extends
      Descriptor<FilesFoundTriggerConfig> {

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
      // Not used.
      return "";
    }

    /**
     * Test the entered trigger configuration.
     * 
     * @param node
     *          the node on which to locate files (the master or a slave node)
     * @param directory
     *          the base directory to use when locating files
     * @param files
     *          the pattern of files to locate under the base directory
     * @param ignoredFiles
     *          the pattern of files to ignore when searching under the base
     *          directory
     * @return the result
     * @throws IOException
     * @throws InterruptedException
     */
    public FormValidation doTestConfiguration(
        @QueryParameter("node") final String node,
        @QueryParameter("directory") final String directory,
        @QueryParameter("files") final String files,
        @QueryParameter("ignoredFiles") final String ignoredFiles)
        throws IOException, InterruptedException {

      FilesFoundTriggerConfig config = new FilesFoundTriggerConfig(node,
          directory, files, ignoredFiles);
      return FileSearch.perform(config.expand()).formValidation;
    }

    /**
     * Get the items to display in the node combo box.
     * 
     * @return the available nodes
     */
    public ComboBoxModel doFillNodeItems() {
      List<Node> nodes = Jenkins.getInstance().getNodes();
      ComboBoxModel model = new ComboBoxModel(nodes.size() + 1);
      model.add("master");
      for (Node node : nodes) {
        model.add(node.getNodeName());
      }
      return model;
    }
  }
}
