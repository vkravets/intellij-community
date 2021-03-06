/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.log;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CollectConsumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.*;
import git4idea.GitVcs;
import git4idea.test.GitSingleRepoTest;
import git4idea.test.GitTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.vcs.Executor.touch;
import static git4idea.test.GitExecutor.*;

public class GitLogProviderTest extends GitSingleRepoTest {

  private GitLogProvider myLogProvider;
  private VcsLogObjectsFactory myObjectsFactory;

  public void setUp() throws Exception {
    super.setUp();
    List<VcsLogProvider> providers =
      ContainerUtil.filter(Extensions.getExtensions(VcsLogManager.LOG_PROVIDER_EP, myProject), new Condition<VcsLogProvider>() {
        @Override
        public boolean value(VcsLogProvider provider) {
          return provider.getSupportedVcs().equals(GitVcs.getKey());
        }
      });
    assertEquals("Incorrect number of GitLogProviders", 1, providers.size());
    myLogProvider = (GitLogProvider)providers.get(0);
    myObjectsFactory = ServiceManager.getService(myProject, VcsLogObjectsFactory.class);
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  public void test_init_with_tagged_branch() throws VcsException {
    prepareSomeHistory();
    List<VcsCommitMetadata> expectedLogWithoutTaggedBranch = log();
    createTaggedBranch();

    VcsLogProvider.DetailedLogData block = myLogProvider.readFirstBlock(myProjectRoot,new RequirementsImpl(1000, false, Collections.<VcsRef>emptySet()));
    assertOrderedEquals(block.getCommits(), expectedLogWithoutTaggedBranch);
  }

  public void test_refresh_with_new_tagged_branch() throws VcsException {
    prepareSomeHistory();
    Set<VcsRef> prevRefs = GitTestUtil.readAllRefs(myProjectRoot, myObjectsFactory);
    createTaggedBranch();

    List<VcsCommitMetadata> expectedLog = log();
    VcsLogProvider.DetailedLogData block = myLogProvider.readFirstBlock(myProjectRoot, new RequirementsImpl(1000, true, prevRefs));
    assertSameElements(block.getCommits(), expectedLog);
  }

  public void test_refresh_when_new_tag_moved() throws VcsException {
    prepareSomeHistory();
    Set<VcsRef> prevRefs = GitTestUtil.readAllRefs(myProjectRoot, myObjectsFactory);
    git("tag -f ATAG");

    List<VcsCommitMetadata> expectedLog = log();
    Set<VcsRef> refs = GitTestUtil.readAllRefs(myProjectRoot, myObjectsFactory);
    VcsLogProvider.DetailedLogData block = myLogProvider.readFirstBlock(myProjectRoot, new RequirementsImpl(1000, true, prevRefs));
    assertSameElements(block.getCommits(), expectedLog);
    assertSameElements(block.getRefs(), refs);
  }

  public void test_new_tag_on_old_commit() throws VcsException {
    prepareSomeHistory();
    Set<VcsRef> prevRefs = GitTestUtil.readAllRefs(myProjectRoot, myObjectsFactory);
    List<VcsCommitMetadata> log = log();
    String firstCommit = log.get(log.size() - 1).getId().asString();
    git("tag NEW_TAG " + firstCommit);

    Set<VcsRef> refs = GitTestUtil.readAllRefs(myProjectRoot, myObjectsFactory);
    VcsLogProvider.DetailedLogData block = myLogProvider.readFirstBlock(myProjectRoot, new RequirementsImpl(1000, true, prevRefs));
    assertSameElements(block.getRefs(), refs);
  }

  public void test_all_log_with_tagged_branch() throws VcsException {
    prepareSomeHistory();
    createTaggedBranch();
    List<VcsCommitMetadata> expectedLog = log();
    List<TimedVcsCommit> collector = ContainerUtil.newArrayList();
    //noinspection unchecked
    myLogProvider.readAllHashes(myProjectRoot, new CollectConsumer<TimedVcsCommit>(collector));
    assertOrderedEquals(expectedLog, collector);
  }

  public void test_get_current_user() throws Exception {
    VcsUser user = myLogProvider.getCurrentUser(myProjectRoot);
    assertNotNull("User is not defined", user);
    VcsUser expected = getDefaultUser();
    assertEquals("User name is incorrect", expected.getName(), user.getName());
    assertEquals("User email is incorrect", expected.getEmail(), user.getEmail());
  }

  public void test_dont_report_origin_HEAD() throws Exception {
    prepareSomeHistory();
    git("update-ref refs/remotes/origin/HEAD master");

    VcsLogProvider.DetailedLogData block = myLogProvider.readFirstBlock(myProjectRoot,
                                                                        new RequirementsImpl(1000, false, Collections.<VcsRef>emptySet()));
    assertFalse("origin/HEAD should be ignored", ContainerUtil.exists(block.getRefs(), new Condition<VcsRef>() {
      @Override
      public boolean value(VcsRef ref) {
        return ref.getName().equals("origin/HEAD");
      }
    }));
  }

  private static void prepareSomeHistory() {
    tac("a.txt");
    git("tag ATAG");
    tac("b.txt");
  }

  private static void tac(@NotNull String file) {
    touch(file, "content" + Math.random());
    addCommit("touched " + file);
  }

  private static void createTaggedBranch() {
    String hash = last();
    tac("c.txt");
    tac("d.txt");
    tac("e.txt");
    git("tag poor-tag");
    git("reset --hard " + hash);
  }

  @NotNull
  private static VcsUser getDefaultUser() {
    return new VcsUserImpl(GitTestUtil.USER_NAME, GitTestUtil.USER_EMAIL);
  }

  @NotNull
  private List<VcsCommitMetadata> log() {
    String output = git("log --all --date-order --full-history --sparse --pretty='%H|%P|%ct|%s|%B'");
    final VcsUser defaultUser = getDefaultUser();
    final Function<String, Hash> TO_HASH = new Function<String, Hash>() {
      @Override
      public Hash fun(String s) {
        return HashImpl.build(s);
      }
    };
    return ContainerUtil.map(StringUtil.splitByLines(output), new Function<String, VcsCommitMetadata>() {
      @Override
      public VcsCommitMetadata fun(String record) {
        String[] items = ArrayUtil.toStringArray(StringUtil.split(record, "|", true, false));
        long time = Long.valueOf(items[2]);
        return new VcsCommitMetadataImpl(TO_HASH.fun(items[0]), ContainerUtil.map(items[1].split(" "), TO_HASH), time,
                                         myProjectRoot, items[3], defaultUser, items[4], defaultUser, time);
      }
    });
  }
}
