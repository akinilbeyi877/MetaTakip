import os
import subprocess
import openai

openai.api_key = os.getenv("OPENAI_API_KEY")

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

def ask_ai(prompt, code):
    response = openai.ChatCompletion.create(
        model="gpt-3.5-turbo",
        messages=[
            {
                "role": "system",
                "content": "You are an Android Kotlin developer. Return only updated code."
            },
            {
                "role": "user",
                "content": f"""
PROMPT:
{prompt}

CODE:
{code}
"""
            }
        ],
        temperature=0.2
    )

    return response["choices"][0]["message"]["content"]

def patch_file():
    file_path = input("Dosya yolu: ").strip()

    if not os.path.exists(file_path):
        print("Dosya bulunamadı")
        return

    with open(file_path, "r", encoding="utf-8") as f:
        old_code = f.read()

    prompt = input("AI prompt: ")

    print("\nAI düşünüyor...\n")

    new_code = ask_ai(prompt, old_code)

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(new_code)

    print("\nPatch uygulandı.\n")

    run(f'git diff "{file_path}"')

def commit_push():
    msg = input("Commit mesajı: ")

    run("git add .")
    run(f'git commit -m "{msg}"')
    run("git push")

def main():
    while True:
        print("\n=== MetaTakip AI Agent ===")
        print("1 = AI patch uygula")
        print("2 = git diff")
        print("3 = commit + push")
        print("4 = çıkış")

        choice = input("\nSeçim: ").strip()

        if choice == "1":
            patch_file()

        elif choice == "2":
            run("git diff")

        elif choice == "3":
            commit_push()

        elif choice == "4":
            break

        else:
            print("Geçersiz seçim")

if __name__ == "__main__":
    main()
