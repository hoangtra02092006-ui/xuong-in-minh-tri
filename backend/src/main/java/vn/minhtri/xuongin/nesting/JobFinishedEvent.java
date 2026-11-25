package vn.minhtri.xuongin.nesting;

import vn.minhtri.xuongin.job.Job;

/**
 * Published once a job reaches DONE or FAILED. The export module listens to start the
 * background TIF build without nesting depending on export.
 */
public record JobFinishedEvent(Job job) {}
