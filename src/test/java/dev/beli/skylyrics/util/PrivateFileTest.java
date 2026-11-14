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
			assertEquals(owner, e.principal());
		}
		Files.writeString(file, "still writable"); // the owner can still use it
	}
}
