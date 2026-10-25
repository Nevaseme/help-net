import unittest
from analyze_motion_observation import summarize


def row(seconds, count, idle=True, interactive=False, phase="sample", age=0.01):
    return dict(phase=phase, elapsedNanos=int(seconds*1e9), deviceIdle=idle,
                interactive=interactive, powerSave=True, optimizationExempt=True,
                status="OBSERVING", positionActive=True,
                observation=dict(startedElapsedNanos=1, sampleCount=count,
                                 lastSampleElapsedNanos=int((seconds-age)*1e9),
                                 maxSampleGapNanos=10000000, maxDeliveryDelayNanos=10000000))


class MotionEvidenceTest(unittest.TestCase):
    def test_steady_screen_off_idle_samples_are_counted(self):
        result = summarize([row(10, 1000, phase="started"), row(15, 1500), row(20, 2000, phase="completed")])
        self.assertEqual(10, result["conditions"]["screenOffIdle"]["seconds"])
        self.assertEqual(100, result["conditions"]["screenOffIdle"]["hz"])
        self.assertEqual([], result["problems"])

    def test_idle_only_at_start_does_not_prove_idle_observation(self):
        result = summarize([row(10, 1000), row(15, 1500, idle=False, phase="completed")])
        self.assertEqual(0, result["conditions"]["screenOffIdle"]["seconds"])

    def test_registration_without_new_samples_is_not_evidence(self):
        result = summarize([row(10, 1000), row(15, 1000, phase="completed", age=5)])
        self.assertEqual(0, result["conditions"]["screenOffIdle"]["seconds"])
        self.assertIn("stalled-or-stale", result["problems"])

    def test_capture_gap_and_process_restart_are_not_silently_ignored(self):
        records = [row(10, 1000), row(40, 4000), row(45, 100, phase="completed")]
        records[-1]["observation"]["startedElapsedNanos"] = int(42e9)
        result = summarize(records)
        self.assertIn("capture-gap", result["problems"])
        self.assertIn("observation-restarted", result["problems"])
        self.assertEqual(0, result["conditions"]["screenOffIdle"]["seconds"])


if __name__ == "__main__":
    unittest.main()
