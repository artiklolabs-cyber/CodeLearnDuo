import io
import json
import sys
import time
import traceback


def run_user_code(code, stdin_text):
    """Execute user code with captured stdin/stdout/stderr and return a JSON string."""
    old_stdin, old_stdout, old_stderr = sys.stdin, sys.stdout, sys.stderr
    out_buf = io.StringIO()
    err_buf = io.StringIO()
    sys.stdin = io.StringIO(stdin_text or "")
    sys.stdout = out_buf
    sys.stderr = err_buf
    success = True
    started = time.time()
    try:
        compiled = compile(code, "<editor.py>", "exec")
        namespace = {"__name__": "__main__"}
        exec(compiled, namespace)
    except SystemExit as exit_info:
        if exit_info.code not in (None, 0):
            success = False
            err_buf.write("Process exited with code {}\n".format(exit_info.code))
    except BaseException:
        success = False
        err_buf.write(traceback.format_exc())
    finally:
        sys.stdin = old_stdin
        sys.stdout = old_stdout
        sys.stderr = old_stderr
    elapsed_ms = int((time.time() - started) * 1000)
    return json.dumps({
        "success": success,
        "stdout": out_buf.getvalue(),
        "stderr": err_buf.getvalue(),
        "elapsedMs": elapsed_ms,
    })
