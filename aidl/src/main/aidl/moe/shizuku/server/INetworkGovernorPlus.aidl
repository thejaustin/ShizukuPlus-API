package moe.shizuku.server;

interface INetworkGovernorPlus {
    /**
     * Set the system-wide Private DNS mode and hostname.
     */
    boolean setPrivateDns(String mode, String hostname);

    /**
     * Add a package to the system's restricted network list (firewall).
     */
    boolean restrictAppNetwork(String packageName, boolean restricted);

    /**
     * Get the current network restriction state for a package.
     */
    boolean isAppNetworkRestricted(String packageName);
}
