package dev.beli.skylyrics.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
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
	 * The whole access list for a private file: one ALLOW entry with every right for each of the
	 * given people (de-duplicated), nobody else. Because it is the complete list, nothing is
	 * inherited from the folder (on a secondary NTFS drive that would include "Authenticated
	 * Users: Modify").
	 */
	public static List<AclEntry> ownerOnlyAcl(UserPrincipal... people) {
		List<AclEntry> acl = new ArrayList<>();
		Set<UserPrincipal> seen = new LinkedHashSet<>();
		for (UserPrincipal who : people) {
			if (who != null && seen.add(who)) {
				acl.add(AclEntry.newBuilder()
						.setType(AclEntryType.ALLOW)
						.setPrincipal(who)
						.setPermissions(EnumSet.allOf(AclEntryPermission.class))
						.build());
			}
		}
		return acl;
	}

	/**
	 * Restricts the file to its owner and the signed-in user if the file system has ACLs. Does
	 * nothing where it doesn't. If it fails, or would lock the player out of their own file (an
	 * admin-created file can be owned by the Administrators group, which a normal game process
	 * only holds as deny-only), the file is recreated empty with the folder's normal access, the
	 * problem is logged once, and the caller carries on.
	 * @return true if the file is now restricted
	 */
	public static boolean restrictToOwner(Path file) {
		AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
		if (view == null) {
			return false;
		}
		return restrict(file, view, PrivateFile::usable);
	}

	static boolean usable(Path file) {
		return Files.isReadable(file) && Files.isWritable(file);
	}

	static boolean restrict(Path file, AclFileAttributeView view, Predicate<Path> usable) {
		try {
			UserPrincipal owner = Files.getOwner(file);
			UserPrincipal me = null;
			try {
				me = file.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
			} catch (IOException | RuntimeException e) {
				// the owner alone will do if this fails
			}
			view.setAcl(ownerOnlyAcl(owner, me));
			if (usable.test(file)) {
				return true;
			}
			warn(file, "it would lock you out of it");
		} catch (IOException | RuntimeException e) {
			warn(file, e.toString());
		}
		try {
			Files.deleteIfExists(file);
			Files.createFile(file);
		} catch (IOException | RuntimeException e) {
			warn(file, e.toString());
		}
		return false;
	}

	private static void warn(Path file, String why) {
		if (WARNED.compareAndSet(false, true)) {
			LOG.warn("Couldn't limit access to {} to your account: {}", file.getFileName(), why);
		}
	}
}
