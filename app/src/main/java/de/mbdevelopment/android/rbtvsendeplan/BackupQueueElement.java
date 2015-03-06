package de.mbdevelopment.android.rbtvsendeplan;

/**
 * Data type for backup queue elements containing on object an it's destination filename
 */
class BackupQueueElement {

    /**
     * The object to be backed up
     */
    public Object element;

    /**
     * Name of the file to be used for the backup
     */
    public String filename;
}
