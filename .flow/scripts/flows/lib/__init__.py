# Scripts library utilities
# This module uses try/except to support both package imports and direct sys.path imports

try:
    # Try relative imports first (when used as a package)
    from .git_utils import (
        commit_and_push_changes,
        format_commits,
        get_flow_commits,
        has_flow_commits,
    )
    from .guide_utils import generate_review_guide
    from .interactive_utils import (
        clean_feedback_markers,
        display_clarification_questions,
        display_diff,
        display_suggestions,
        parse_clarification_from_report,
        parse_inline_feedback,
        parse_suggestions_from_report,
        prompt_human_review,
        resolve_feedback_markers,
    )
    from .learnings_runner import run_learnings_analysis
    from .orchestration_utils import InfoTracker
    from .proof_runner import run_proof_generation
    from .review_loop import (
        ReviewLoopConfig,
        ReviewLoopResult,
        check_doc_review_approved,
        check_review_approved,
        check_step_review_approved,
        run_review_loop,
    )
    from .validation_utils import run_validation, truncate_validation_output
except ImportError:
    # Fall back to direct imports (when lib dir is in sys.path)
    from git_utils import (  # type: ignore[import-not-found,no-redef]
        commit_and_push_changes,
        format_commits,
        get_flow_commits,
        has_flow_commits,
    )
    from guide_utils import generate_review_guide  # type: ignore[import-not-found,no-redef]
    from interactive_utils import (  # type: ignore[import-not-found,no-redef]
        clean_feedback_markers,
        display_clarification_questions,
        display_diff,
        display_suggestions,
        parse_clarification_from_report,
        parse_inline_feedback,
        parse_suggestions_from_report,
        prompt_human_review,
        resolve_feedback_markers,
    )
    from learnings_runner import run_learnings_analysis  # type: ignore[import-not-found,no-redef]
    from orchestration_utils import InfoTracker  # type: ignore[import-not-found,no-redef]
    from proof_runner import run_proof_generation  # type: ignore[import-not-found,no-redef]
    from review_loop import (  # type: ignore[import-not-found,no-redef]
        ReviewLoopConfig,
        ReviewLoopResult,
        check_doc_review_approved,
        check_review_approved,
        check_step_review_approved,
        run_review_loop,
    )
    from validation_utils import run_validation, truncate_validation_output  # type: ignore[import-not-found,no-redef]

__all__ = [
    # git_utils
    "commit_and_push_changes",
    "format_commits",
    "get_flow_commits",
    "has_flow_commits",
    # guide_utils
    "generate_review_guide",
    # interactive_utils
    "clean_feedback_markers",
    "display_clarification_questions",
    "display_diff",
    "display_suggestions",
    "parse_clarification_from_report",
    "parse_inline_feedback",
    "parse_suggestions_from_report",
    "prompt_human_review",
    "resolve_feedback_markers",
    # learnings_runner
    "run_learnings_analysis",
    # proof_runner
    "run_proof_generation",
    # orchestration_utils
    "InfoTracker",
    # review_loop
    "ReviewLoopConfig",
    "ReviewLoopResult",
    "check_doc_review_approved",
    "check_review_approved",
    "check_step_review_approved",
    "run_review_loop",
    # validation_utils
    "run_validation",
    "truncate_validation_output",
]
