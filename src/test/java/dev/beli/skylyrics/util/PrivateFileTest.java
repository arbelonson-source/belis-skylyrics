package dev.beli.skylyrics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class PrivateFileTest {
	private record Who(String name) implements UserPrincipal {
		@Override
		public String getName() {
			return name;
		}
	}

	@Test
	void theListHoldsOnlyTheOwnerWithFullRights() {
		UserPrincipal me = new Who("me");
		List<AclEntry> acl = PrivateFile.ownerOnlyAcl(me);
		assertEquals(1, acl.size());
		assertEquals(me, acl.get(0).principal());
		assertEquals(AclEntryType.ALLOW, acl.get(0).type());
		assertEquals(EnumSet.allOf(AclEntryPermission.class), acl.get(0).permissions());
	}

	@Test
	void ownerAndCurrentUserBothGetAnEntryButTheSamePersonOnlyOnce() {
		UserPrincipal admins = new Who("Administrators");
		UserPrincipal me = new Who("me");
		List<AclEntry> two = PrivateFile.ownerOnlyAcl(admins, me);
		assertEquals(List.of(admins, me), two.stream().map(AclEntry::principal).toList());
		assertEquals(1, PrivateFile.ownerOnlyAcl(me, new Who("me")).size());
		assertEquals(1, PrivateFile.ownerOnlyAcl(me, null).size());
	}

	@Test
	void aLockedOutFileIsRecreatedPlainly(@TempDir Path dir) throws IOException {
		Path file = Files.writeString(dir.resolve("t.json"), "x");
		boolean[] set = {false};
		AclFileAttributeView view = new AclFileAttributeView() {
			public String name() {
				return "acl";
			}

			public List<AclEntry> getAcl() {
				return List.of();
			}

			public void setAcl(List<AclEntry> acl) {
				set[0] = true;
			}

			public UserPrincipal getOwner() {
				return new Who("o");
			}

			public void setOwner(UserPrincipal owner) {
			}
		};
		assertFalse(PrivateFile.restrict(file, view, f -> false));
		assertTrue(set[0]);
		assertTrue(Files.isRegularFile(file));
		assertEquals(0, Files.size(file));
		Files.writeString(file, "usable again");
	}

	@Test
	void anAccessibleFileStaysRestricted(@TempDir Path dir) throws IOException {
		Path file = Files.createFile(dir.resolve("t.json"));
		AclFileAttributeView view = new AclFileAttributeView() {
			public String name() {
				return "acl";
			}

			public List<AclEntry> getAcl() {
				return List.of();
			}

			public void setAcl(List<AclEntry> acl) {
			}

			public UserPrincipal getOwner() {
				return new Who("o");
			}

			public void setOwner(UserPrincipal owner) {
			}
		};
		assertTrue(PrivateFile.restrict(file, view, f -> true));
	}

	@Test
	void doesNothingWhereThereAreNoAcls(@TempDir Path dir) throws IOException {
		Path file = Files.createFile(dir.resolve("t.json"));
		boolean hasAcl = Files.getFileAttributeView(file, AclFileAttributeView.class) != null;
		assertEquals(hasAcl, PrivateFile.restrictToOwner(file));
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void onlyTheOwnerIsLeftOnARealFile(@TempDir Path dir) throws IOException {
		Path file = Files.createFile(dir.resolve("t.json"));
		assertTrue(PrivateFile.restrictToOwner(file));
		UserPrincipal owner = Files.getOwner(file);
		List<AclEntry> acl = Files.getFileAttributeView(file, AclFileAttributeView.class).getAcl();
		assertFalse(acl.isEmpty());
		for (AclEntry e : acl) {
			assertTrue(e.principal().equals(owner) || e.principal().getName().endsWith("\\" + System.getProperty("user.name")), e.principal().getName());
		}
		Files.writeString(file, "still writable"); // the owner can still use it
	}
}
