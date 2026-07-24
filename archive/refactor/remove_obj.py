import os

OBJ_FILE = 'src/main/java/hara/lang/base/Obj.java'

def remove_obj():
    if os.path.exists(OBJ_FILE):
        os.remove(OBJ_FILE)
        print(f"Deleted {OBJ_FILE}")
    else:
        print(f"{OBJ_FILE} does not exist.")

if __name__ == '__main__':
    remove_obj()
