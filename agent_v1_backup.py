import os
import subprocess

PROJECT_DIR = os.getcwd()

def run(cmd):
    print(f"\n>>> {cmd}\n")
    result = subprocess.run(
        cmd,
        shell=True,
        text=True,
        capture_output=True
    )

    if result.stdout:
        print(result.stdout)

    if result.stderr:
        print(result.stderr)

    return result.returncode

def show_status():
    run("git status")

def show_diff():
    run("git diff")

def commit_and_push(message):
    run("git add .")
    run(f'git commit -m "{message}"')
    run("git push")

def main():
    print("=== MetaTakip Agent ===")

    while True:
        print("\nKomutlar:")
        print("1 = git status")
        print("2 = git diff")
        print("3 = commit + push")
        print("4 = çıkış")

        choice = input("\nSeçim: ").strip()

        if choice == "1":
            show_status()

        elif choice == "2":
            show_diff()

        elif choice == "3":
            msg = input("Commit mesajı: ")
            commit_and_push(msg)

        elif choice == "4":
            break

        else:
            print("Geçersiz seçim")

if __name__ == "__main__":
    main()
