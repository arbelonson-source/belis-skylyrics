package dev.beli.skylyrics.util;

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
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Makes a file readable by its owner only on file systems with ACLs (Windows/NTFS). */
public final class PrivateFile {
	private static final Logger LOG = LoggerFactory.getLogger("skylyrics");
	private static final AtomicBoolean WARNED = new AtomicBoolean();

	private PrivateFile() {
	}

	/**
	 * The whole access list for a private file: one entry giving the owner every right and nobody
	 * else anything. Because it is the complete list, nothing is inherited from the folder
	 * (on a secondary NTFS drive that would include "Authenticated Users: Modify").
	 */
	public static List<AclEntry> ownerOnlyAcl(UserPrincipal owner) {
		return List.of(AclEntry.newBuilder()
				.setType(AclEntryType.ALLOW)
				.setPrincipal(owner)
				.setPermissions(EnumSet.allOf(AclEntryPermission.class))
				.build());
	}

	/**
	 * Restricts the file to its owner if the file system has ACLs. Does nothing where it doesn't;
	 * if it fails, says so once and carries on.
	 * @return true if the ACL was set
	 */
	public static boolean restrictToOwner(Path file) {
		try {
			AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
			if (view == null) {
				return false;
			}
			view.setAcl(ownerOnlyAcl(Files.getOwner(file)));
			return true;
		} catch (IOException | RuntimeException e) {
			if (WARNED.compareAndSet(false, true)) {
				LOG.warn("Couldn't limit access to {} to your account: {}", file.getFileName(), e.toString());
			}
			return false;
		}
	}
}
