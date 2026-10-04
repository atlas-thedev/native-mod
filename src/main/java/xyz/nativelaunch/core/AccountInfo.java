package xyz.nativelaunch.core;

/** The Noctra account a game ticket belongs to. */
public final class AccountInfo {
	public final String id;
	public final String name;
	public final String uuid;
	public final String model;

	AccountInfo(String id, String name, String uuid, String model) {
		this.id = id;
		this.name = name;
		this.uuid = uuid;
		this.model = model;
	}
}
