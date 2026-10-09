/*
 * Copyright 2026 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.hadoop.fs.gcs;

import static com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystemConfiguration.GCS_LIST_FILES_MODE;
import static com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystemTestHelper.createInMemoryGoogleHadoopFileSystem;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem.ListFilesMode;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.junit.Test;

/** Tests the opt-in contract-correct GHFS listFiles implementation. */
public class GoogleHadoopFileSystemListFilesTest {

  @Test
  public void pagedMode_preservesFileSystemContract() throws Exception {
    Configuration config = new Configuration();
    config.setEnum(GCS_LIST_FILES_MODE.getKey(), ListFilesMode.PAGED);

    try (GoogleHadoopFileSystem pagedFs = createInMemoryGoogleHadoopFileSystem(config)) {
      Path root = new Path("/paged-list-files");
      Path nested = new Path(root, "nested");
      Path topLevelFile = new Path(root, "top-level.parquet");
      Path nestedFile = new Path(nested, "nested.parquet");
      pagedFs.mkdirs(nested);
      try (FSDataOutputStream output = pagedFs.create(topLevelFile)) {
        output.write(1);
      }
      try (FSDataOutputStream output = pagedFs.create(nestedFile)) {
        output.write(2);
      }

      List<String> recursiveFiles = new ArrayList<>();
      RemoteIterator<LocatedFileStatus> recursiveIterator = pagedFs.listFiles(root, true);
      while (recursiveIterator.hasNext()) {
        recursiveFiles.add(recursiveIterator.next().getPath().toString());
      }
      assertThat(recursiveFiles)
          .containsExactly(
              pagedFs.makeQualified(nestedFile).toString(),
              pagedFs.makeQualified(topLevelFile).toString());

      List<String> directFiles = new ArrayList<>();
      RemoteIterator<LocatedFileStatus> directIterator = pagedFs.listFiles(root, false);
      while (directIterator.hasNext()) {
        directFiles.add(directIterator.next().getPath().toString());
      }
      assertThat(directFiles).containsExactly(pagedFs.makeQualified(topLevelFile).toString());

      RemoteIterator<LocatedFileStatus> fileIterator = pagedFs.listFiles(topLevelFile, true);
      assertThat(fileIterator.hasNext()).isTrue();
      assertThat(fileIterator.next().getPath()).isEqualTo(pagedFs.makeQualified(topLevelFile));
      assertThat(fileIterator.hasNext()).isFalse();

      assertThrows(
          FileNotFoundException.class,
          () -> pagedFs.listFiles(new Path(root, "missing.parquet"), true));
    }
  }
}
